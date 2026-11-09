#!/usr/bin/env python3
"""Package reproducible source/docs without keys, caches or binary build output."""
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]

def package():
    files=set(ROOT.glob('*.md'))|set(ROOT.glob('*.json'))|{ROOT/'THIRD_PARTY_NOTICES.txt',ROOT/'.gitignore',ROOT/'.openai/hosting.json'}
    for directory in ('android','scripts','app','lib','worker','db','drizzle','web-test','vendor'):
        files.update(p for p in (ROOT/directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
    files.update(p for p in (ROOT/'public').iterdir() if p.is_file() and p.suffix in ('.html','.css','.mjs','.svg'))
    files.add(ROOT/'public/_headers')
    files.update(ROOT/name for name in ('vite.config.ts','drizzle.config.ts','tsconfig.json','next.config.ts','next-env.d.ts','cloudflare-env.d.ts','pnpm-lock.yaml','pnpm-workspace.yaml','postcss.config.mjs') if (ROOT/name).exists())
    destination=ROOT/'public/SquashTap-source.zip'
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(files):
            relative=file.relative_to(ROOT)
            if any(part in ('.local','build','.git','__pycache__') for part in relative.parts) or file.suffix in ('.p12','.password','.jks','.keystore','.apk'):
                raise RuntimeError('Private/build file in source list: '+str(relative))
            entry=zipfile.ZipInfo('SquashTap/'+relative.as_posix(),(2026,11,3,0,0,0))
            entry.compress_type=zipfile.ZIP_DEFLATED
            entry.external_attr=0o644<<16
            archive.writestr(entry,file.read_bytes())
    print('Source download:',destination,'('+str(len(files))+' files)')

if __name__=='__main__':package()
