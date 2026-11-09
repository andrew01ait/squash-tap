#!/usr/bin/env python3
"""Keep publicly distributed APK history from v0.3.0 (version code 3)."""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
MIN_VERSION_CODE=3

def prune(root=ROOT):
    metadata=root/'public/builds.json'
    if not metadata.exists():return []
    builds=json.loads(metadata.read_text())
    retained=sorted((item for item in builds if item['versionCode']>=MIN_VERSION_CODE),key=lambda item:item['versionCode'])
    retired=[item for item in builds if item['versionCode']<MIN_VERSION_CODE]
    # Validate the complete deletion list before changing any files.
    kept_names={item['filename'] for item in retained}
    for item in retired:
        name=item['filename']
        if Path(name).name!=name or not name.startswith('SquashTap-') or not name.endswith('.apk') or name in kept_names:
            raise ValueError('Invalid retired APK filename')
    for item in retired:
        apk=root/'public/apks'/item['filename']
        apk.unlink(missing_ok=True)
        apk.with_name(apk.name+'.idsig').unlink(missing_ok=True)
    metadata.write_text(json.dumps(retained,indent=2)+'\n')
    (root/'public/SHA256SUMS.txt').write_text(''.join(item['sha256']+'  '+item['filename']+'\n' for item in retained))
    return retained

if __name__=='__main__':
    kept=prune()
    print('Retained public APKs: '+', '.join(item['versionName'] for item in kept))
