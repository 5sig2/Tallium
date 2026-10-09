from pathlib import Path
from collections import Counter,defaultdict
import hashlib
import json
import sys
import zipfile

def category(name):
    if name.startswith('assets/'):return 'shared_assets'
    if '/flat/mixins/Native_' in name:return 'native_api_hooks'
    if '/flat/mixins/Transform_' in name or name.startswith('META-INF/tallium/'):return 'adapter_selection_and_linkage'
    if '/flat/shared/Shape_' in name:return 'adapter_class_shapes'
    if '/flat/shared/' in name:return 'shared_ui_and_client_implementation'
    if '/adapter/' in name:return 'neutral_class_targets'
    if '/flat/' in name:return 'bootstrap_and_transformation'
    if name.endswith('.class'):return 'minecraft_independent_core'
    return 'metadata_and_notices'

def check(path):
    with zipfile.ZipFile(path) as jar:
        names=jar.namelist();duplicates=[n for n,c in Counter(names).items() if c>1]
        assert not duplicates,('Duplicate ZIP paths',duplicates)
        assert jar.testzip() is None
        assert names.count('fabric.mod.json')==1
        metadata=json.loads(jar.read('fabric.mod.json'))
        assert metadata['id']=='tallium' and not metadata.get('jars')
        assert set(metadata['depends'])=={'fabricloader','minecraft','java'}
        assert metadata['custom']['tallium']['packaging']=='flat-shared-methods'
        diagnostics=('UiGallery','UiSmoke','GameplayHarness','ObserverHarness','KeyHarness','InspectionHarness','CoreVerification','FlatPack')
        forbidden=('net/fabricmc/','org/objectweb/','org/spongepowered/','com/terraformersmc/','com/google/gson/')
        assets={};totals=defaultdict(lambda:dict(entries=0,compressedBytes=0,uncompressedBytes=0))
        for entry in jar.infolist():
            name=entry.filename;raw=jar.read(name)
            assert not name.lower().endswith(('.jar','.zip','.7z','.tar','.gz','.bz2','.xz','.class.b64','.java','.log')),('Forbidden archive/development entry',name)
            assert not raw.startswith((b'PK\x03\x04',b'7z\xbc\xaf\x27\x1c',b'\x1f\x8b')),('Embedded archive payload',name)
            assert not name.startswith(forbidden),('Embedded platform dependency',name)
            assert not any(marker in name for marker in diagnostics),('Diagnostic file',name)
            assert not (name.endswith('fabric.mod.json') and name!='fabric.mod.json'),('Obsolete runtime metadata',name)
            assert 'tallium-runtime' not in name and 'tallium-runtimes' not in name
            if name.endswith('.class'):assert b'TRACE_' not in raw and b'UI action failed' not in raw
            if name.startswith('assets/'):
                digest=hashlib.sha256(raw).hexdigest();assert digest not in assets,('Repeated shared asset',name,assets.get(digest));assets[digest]=name
            c=totals[category(name)];c['entries']+=1;c['compressedBytes']+=entry.compress_size;c['uncompressedBytes']+=entry.file_size
        assert 'assets/tallium/icon.png' in names
        expected=Path(__file__).resolve().parents[1]/'resources/assets/tallium/icon.png'
        assert jar.read('assets/tallium/icon.png')==expected.read_bytes(),'Asset quality/bytes changed'

        assert not any('/versions/' in n and n.endswith('.class') for n in names)
        assert not any(n.startswith(('META-INF/versions/','META-INF/jars/')) for n in names)
        return dict(status='PASSED',bytes=Path(path).stat().st_size,uncompressedBytes=sum(e.file_size for e in jar.infolist()),
            compressedEntryBytes=sum(e.compress_size for e in jar.infolist()),zipOverheadBytes=Path(path).stat().st_size-sum(e.compress_size for e in jar.infolist()),
            entries=len(names),nestedArchives=0,modIdentities=1,assetsIncludedOnce=True,developmentFiles=False,embeddedDependenciesBytes=0,categories=dict(totals))

if __name__=='__main__':
    result=check(Path(sys.argv[1]));print(json.dumps(result,indent=2))
