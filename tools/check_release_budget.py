from pathlib import Path
import json
from check_flat_package import check
from flat_build import java
from check_flat_ownership import check_owned

ROOT=Path(__file__).resolve().parents[1]

def main():
    matrix=json.loads((ROOT/'versions.json').read_text());version=matrix['modVersion']
    jar=ROOT/f'dist/tallium-{version}'/matrix['releaseFile']
    result=check(jar)
    result['ownership']=check_owned(jar)
    java('audit',jar)
    budget=json.loads((ROOT/'tools/flat-size-budget.json').read_text())
    assert result['bytes']<=budget['maxCompressedBytes'],('Compressed release size exceeds measured budget',result['bytes'],budget)
    assert result['uncompressedBytes']<=budget['maxUncompressedBytes'],('Uncompressed release size exceeds measured budget',result['uncompressedBytes'],budget)
    print(json.dumps(dict(status='PASSED',bytes=result['bytes'],uncompressedBytes=result['uncompressedBytes'],budget=budget),indent=2))
    return result

if __name__=='__main__':main()
