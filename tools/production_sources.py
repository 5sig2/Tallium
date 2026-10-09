from pathlib import Path
import hashlib

ROOT=Path(__file__).resolve().parents[1]

def source_files():
    return sorted(set(list((ROOT/'adapters/template').glob('*.java'))+
        list((ROOT/'core/src/main/java').rglob('*.java'))+list((ROOT/'flat/src/main/java').rglob('*.java'))+
        [p for p in (ROOT/'resources').rglob('*') if p.is_file()]+
        [ROOT/name for name in ('build.gradle','settings.gradle','core/build.gradle','flat/build.gradle',
          'LICENSE','THIRD_PARTY_NOTICES.txt','tools/adapter.gradle','tools/generate_adapters.py','tools/FlatPack.java','tools/flat_build.py','tools/check_flat_package.py')]))

def source_hashes():return {p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files()}
