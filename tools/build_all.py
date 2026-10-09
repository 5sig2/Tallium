import json,os,pathlib,subprocess,zipfile,hashlib,shutil,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
matrix=json.loads((ROOT/'versions.json').read_text())
gradle=os.environ.get('TALLIUM_GRADLE',str(ROOT/('gradlew.bat' if os.name=='nt' else 'gradlew')))
order=['1.21.11','1.21','26.2']+[t['minecraft'] for t in matrix['targets'] if t['minecraft'] not in ['1.21.11','1.21','26.2']]
selected=sys.argv[1:] or order
from production_sources import source_files as release_source_files
source_files=release_source_files()
source_hashes={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files}
report={'versions':[], 'sourceHashes':source_hashes}
for version in selected:
    target=next(t for t in matrix['targets'] if t['minecraft']==version)
    log=ROOT/f"verification/releases/{matrix['modVersion']}/build-logs/build-{version}.log";log.parent.mkdir(parents=True,exist_ok=True);env=os.environ.copy()
    if env.get('TALLIUM_JDK25'):env['JAVA_HOME']=env['TALLIUM_JDK25']
    with log.open('w',encoding='utf-8') as out:
        result=subprocess.run([gradle,f'-Ptarget={version}',':adapter:clean',':adapter:distribute','--console=plain'],cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
    status='passed' if result.returncode==0 else 'failed'
    jar=ROOT/f"verification/local/flat-inputs/{matrix['modVersion']}"/target['artifact']
    if result.returncode==0:
        with zipfile.ZipFile(jar) as z:
            metadata=json.loads(z.read('fabric.mod.json'));assert metadata['depends']['minecraft']=='='+version
            assert metadata['version']==matrix['modVersion']+'+mc'+version
            assert set(metadata['depends'])=={'fabricloader','minecraft','java'}
            assert not any(x.startswith(('net/fabricmc/fabric/api/','net/uku3lig/','me/shedaniel/')) or x.startswith('META-INF/jars/') for x in z.namelist())
            assert 'dev/sig/tallium/tracking/CounterStore.class' in z.namelist()
            assert not any(any(marker in name for marker in ('UiSmoke','UiGallery','GameplayHarness','KeyHarness','ObserverHarness','InspectionHarness')) for name in z.namelist()),'Temporary diagnostics must never ship'
            assert not any(x.startswith('com/terraformersmc/') for x in z.namelist()),'Mod Menu must remain optional and unshaded'
            assert metadata['entrypoints']['modmenu']==['dev.sig.tallium.adapter.TalliumModMenu']
            assert not any(marker in z.read('tallium.mixins.json').decode() for marker in ('UiGallery','UiSmoke'))
            assert all(b'UI action failed' not in z.read(name) for name in z.namelist() if name.endswith('.class')),'Diagnostic action instrumentation must never ship'
            assert int.from_bytes(z.read('dev/sig/tallium/tracking/CounterStore.class')[6:8],'big')==65
            assert int.from_bytes(z.read('dev/sig/tallium/adapter/TalliumClient.class')[6:8],'big')==44+target['java']
            assert metadata['depends']['java']=='>='+str(target['java'])
            assert metadata['authors']==['5Sig']
            assert z.read('THIRD_PARTY_NOTICES.txt')==(ROOT/'THIRD_PARTY_NOTICES.txt').read_bytes()
            assert not any(b'TRACE_' in z.read(name) for name in z.namelist() if name.endswith('.class')),'Diagnostic tracing must never ship'
            assert z.read('assets/tallium/icon.png')==(ROOT/'resources/assets/tallium/icon.png').read_bytes()
        artifact_sha256=hashlib.sha256(jar.read_bytes()).hexdigest()
        report['versions'].append({'game':version,'status':'PASSED','artifact':target['artifact'],'sha256':artifact_sha256,'java':target['java'],'diagnosticClasses':False,'modMenuEntrypoint':True,'hardDependencies':metadata['depends']})
    print(version,status,flush=True)
    if status!='passed':raise RuntimeError('Native build failed: '+version)
assert all(hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==sha for name,sha in source_hashes.items()),'Production source changed during build; rebuild.'
report_path=ROOT/f"verification/releases/{matrix['modVersion']}/build-verification.json"
report_path.parent.mkdir(parents=True,exist_ok=True)
report_path.write_text(json.dumps(report,indent=2)+'\n')
if len(report['versions'])==len(matrix['targets']):
    inputs=ROOT/f"verification/local/flat-inputs/{matrix['modVersion']}"
    (inputs/'SHA256SUMS.txt').write_text(''.join(f"{r['sha256']}  {r['artifact']}\n" for r in report['versions']))
    shutil.copy2(ROOT/'versions.json',inputs/'version-matrix.json')
sys.exit(0)
