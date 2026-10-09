from pathlib import Path
import hashlib
import json
import os
import subprocess
import tempfile
import zipfile
import shutil

ROOT=Path(__file__).resolve().parents[1]
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
JDK=Path(os.environ.get('TALLIUM_JDK25') or os.environ.get('JAVA_HOME') or Path(shutil.which('javac') or 'javac').resolve().parent.parent)
EXE='.exe' if os.name=='nt' else ''
TOOL_BUILD=ROOT/'verification/local/flat-pack-tool'

def dependency(group,name,version):
    return next(p for p in (CACHE/group/name/version).rglob('*.jar') if not any(s in p.name for s in ('sources','javadoc')))

def classpath():
    return os.pathsep.join(map(str,[dependency('org.ow2.asm',n,'9.10.1') for n in ('asm','asm-tree','asm-commons')]+[
        dependency('net.fabricmc','fabric-loader','0.19.5'),dependency('net.fabricmc','sponge-mixin','0.17.4+mixin.0.8.7')]))

def compile_tools():
    TOOL_BUILD.mkdir(parents=True,exist_ok=True)
    sources=sorted((ROOT/'flat/src/main/java').rglob('*.java'))+[ROOT/'tools/FlatPack.java']
    subprocess.run([str(JDK/('bin/javac'+EXE)),'-proc:none','--release','21','-encoding','UTF-8','-cp',classpath(),'-d',str(TOOL_BUILD),*map(str,sources)],check=True)

def java(*args):
    subprocess.run([str(JDK/('bin/java'+EXE)),'-cp',str(TOOL_BUILD)+os.pathsep+classpath(),'FlatPack',*map(str,args)],check=True)

def properties(raw):
    result={}
    for line in raw.decode('iso-8859-1').splitlines():
        if not line or line.startswith(('#','!')):continue
        k,v=line.split('=',1)
        result[k.replace('\\:',':').replace('\\=','=')]=v.replace('\\:',':').replace('\\=','=')
    return result

def input_dir(version):return ROOT/f'verification/local/flat-inputs/{version}'

def pack(destination=None,matrix=None):
    matrix=matrix or json.loads((ROOT/'versions.json').read_text());version=matrix['modVersion']
    destination=destination or ROOT/f'dist/tallium-{version}'/matrix['releaseFile']
    report_dir=ROOT/f'verification/releases/{version}';report_dir.mkdir(parents=True,exist_ok=True)
    from production_sources import source_hashes
    before=source_hashes()
    build=json.loads((report_dir/'build-verification.json').read_text())
    assert before==build['sourceHashes'],'Production sources differ from the native compilation inputs'
    compile_tools();inputs=[];proof=[]
    for target in matrix['targets']:
        path=input_dir(version)/target['artifact'];raw=path.read_bytes();digest=hashlib.sha256(raw).hexdigest()
        record=next(r for r in build['versions'] if r['game']==target['minecraft'])
        assert digest==record['sha256'],('Stale native compilation input',target['minecraft'])
        inputs.extend([target['minecraft'],str(path)])
        proof.append(dict(game=target['minecraft'],input=path.relative_to(ROOT).as_posix(),sha256=digest,java=target['java']))
    destination.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='flat-package-',dir=ROOT/'verification/local') as temporary:
        files=Path(temporary);java(files,*inputs)
        payload={p.relative_to(files).as_posix():p.read_bytes() for p in files.rglob('*') if p.is_file()}
        for p in (TOOL_BUILD/'dev/sig/tallium/flat').glob('*.class'):
            payload[p.relative_to(TOOL_BUILD).as_posix()]=p.read_bytes()
        for p in (ROOT/'resources/assets').rglob('*'):
            if p.is_file():payload[p.relative_to(ROOT/'resources').as_posix()]=p.read_bytes()
        payload['THIRD_PARTY_NOTICES.txt']=(ROOT/'THIRD_PARTY_NOTICES.txt').read_bytes()
        payload['LICENSE']=(ROOT/'LICENSE').read_bytes()

        signatures={};groups=[];index=[]
        for target in matrix['targets']:
            name=f'META-INF/tallium/versions/{target["minecraft"]}.properties';raw=payload[name]
            signature=hashlib.sha256(raw).hexdigest()
            if signature not in signatures:
                group='api-'+str(len(groups)+1);signatures[signature]=group
                groups.append(dict(id=group,versions=[],selectionSha256=signature,java=target['java']))
            group=signatures[signature];next(g for g in groups if g['id']==group)['versions'].append(target['minecraft'])
            group_path='META-INF/tallium/groups/'+group+'.properties'
            selected=raw+('group='+group+'\n').encode('ascii')
            if group_path in payload:assert payload[group_path]==selected
            payload[group_path]=selected;payload.pop(name)
            native_names=payload.pop(f'META-INF/tallium/versions/{target["minecraft"]}-mixins.properties')
            mixin_path='META-INF/tallium/groups/'+group+'-mixins.properties'
            if mixin_path in payload:assert payload[mixin_path]==native_names
            payload[mixin_path]=native_names
            index.append(target['minecraft']+'='+group)
        payload['META-INF/tallium/versions.properties']=('\n'.join(index)+'\n').encode('ascii')
        metadata=dict(schemaVersion=1,id='tallium',version=version,name='Tallium',authors=['5Sig'],environment='client',
            description='Item counters for your HUD and player nametags.',license='MIT',
            contact=dict(homepage='https://github.com/5sig2/Tallium',sources='https://github.com/5sig2/Tallium',issues='https://github.com/5sig2/Tallium/issues'),
            icon='assets/tallium/icon.png',entrypoints=dict(client=['dev.sig.tallium.flat.Bootstrap'],modmenu=[dict(adapter='tallium_optional',value='dev.sig.tallium.adapter.TalliumModMenu')]),
            languageAdapters=dict(tallium_optional='dev.sig.tallium.flat.OptionalEntrypoint'),mixins=['tallium.flat.mixins.json'],
            depends=dict(fabricloader='>=0.19.5',minecraft=['='+t['minecraft'] for t in matrix['targets']],java='>=21'),suggests=dict(modmenu='*'),
            custom=dict(tallium=dict(packaging='flat-shared-methods',adapterGroups=groups)))
        config=dict(required=True,package='dev.sig.tallium.flat.mixins',compatibilityLevel='JAVA_21',plugin='dev.sig.tallium.flat.CompatibilityPlugin',client=[],injectors=dict(defaultRequire=1))
        payload['fabric.mod.json']=(json.dumps(metadata,indent=2)+'\n').encode()
        payload['tallium.flat.mixins.json']=(json.dumps(config,indent=2)+'\n').encode()
        with zipfile.ZipFile(destination,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as packed:
            for name,data in sorted(payload.items()):
                info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16
                packed.writestr(info,data,compresslevel=9)

    for target in matrix['targets']:
        with tempfile.NamedTemporaryFile(suffix='.jar',dir=ROOT/'verification/local',delete=False) as handle:temporary=Path(handle.name)
        try:java('helper',destination,input_dir(version)/target['artifact'],temporary,target['minecraft'])
        finally:temporary.unlink(missing_ok=True)
    from check_flat_package import check
    java('audit',destination)
    structural=check(destination)
    assert before==source_hashes(),'Production sources changed while packaging'
    report=dict(status='PACKAGED',architecture='flat-shared-methods',version=version,artifact=destination.relative_to(ROOT).as_posix(),
        sha256=hashlib.sha256(destination.read_bytes()).hexdigest(),bytes=destination.stat().st_size,adapterGroups=groups,
        nativeCompilationInputs=proof,reconstruction='PASSED: exact ASM-normalized production class equality for all sixteen native compile outputs',packagingCheck=structural)
    (report_dir/'universal-package.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ('status','version','artifact','sha256','bytes','adapterGroups')}),flush=True)
    return report

if __name__=='__main__':pack()
