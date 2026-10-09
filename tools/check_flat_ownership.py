from pathlib import Path
import json
import zipfile
from flat_build import ROOT,properties

def check_owned(path):
    with zipfile.ZipFile(path) as jar:
        names=set(jar.namelist());metadata=json.loads(jar.read('fabric.mod.json'))
        owned={'fabric.mod.json','tallium.flat.mixins.json','THIRD_PARTY_NOTICES.txt','LICENSE','META-INF/tallium/versions.properties'}
        owned.update(p.relative_to(ROOT/'resources').as_posix() for p in (ROOT/'resources/assets').rglob('*') if p.is_file())
        for group in metadata['custom']['tallium']['adapterGroups']:
            base='META-INF/tallium/groups/'+group['id']
            owned.update((base+'.properties',base+'-mixins.properties'))
            table=properties(jar.read(base+'.properties'))
            for key,value in table.items():
                if key.endswith('.shape'):owned.update((key[:-6]+'.class',value+'.class'))
                if key.endswith('.methods'):
                    for selected in value.split(','):
                        if not selected:continue
                        method,binding,_=selected.replace('\\#','#').split('|',2)
                        owned.update((method.split('#',1)[0]+'.class',binding))
            owned.update('dev/sig/tallium/flat/mixins/'+name+'.class' for name in table['mixins'].split(','))
        core={p.relative_to(ROOT/'core/src/main/java').as_posix()[:-5] for p in (ROOT/'core/src/main/java').rglob('*.java')}
        bootstrap={p.relative_to(ROOT/'flat/src/main/java').as_posix()[:-5] for p in (ROOT/'flat/src/main/java').rglob('*.java')}
        for name in names:
            if name.endswith('.class') and name[:-6].split('$',1)[0] in core|bootstrap:owned.add(name)
        assert not names-owned,('Unintended development, test or unreferenced payload',sorted(names-owned))
        assert owned<=names,('Missing selected implementation payload',sorted(owned-names))
    return dict(status='PASSED',allEntriesOwned=True,unreferencedPayloads=0)
