import os
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

def main():
    env=os.environ.copy()
    env['PYTHONUTF8']='1'
    gradle=env.get('TALLIUM_GRADLE',str(ROOT/('gradlew.bat' if os.name=='nt' else 'gradlew')))
    if env.get('TALLIUM_JDK25'):
        env['JAVA_HOME']=env['TALLIUM_JDK25']
    commands=[
        [sys.executable,str(ROOT/'tools/generate_adapters.py')],
        [sys.executable,str(ROOT/'tools/build_all.py')],
        [gradle,':flat:classes','--console=plain'],
        [sys.executable,str(ROOT/'tools/package_universal.py')],
        [sys.executable,str(ROOT/'tools/check_release_budget.py')]
    ]
    for command in commands:
        subprocess.run(command,cwd=ROOT,env=env,check=True)

if __name__=='__main__':main()
