#!/usr/bin/env python3
"""Restore checksum-pinned Play tooling without changing the APK release kit."""
from pathlib import Path
import hashlib, json, os, shutil, tempfile, urllib.request, zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    lock = json.loads((ROOT / 'play-build-tools.json').read_text())
    cache = ROOT / '.local/play-tools/cache'
    cache.mkdir(parents=True, exist_ok=True)
    for item in lock['tools']:
        destination = ROOT / item['destination']
        archive = cache / (item['sha256'] + '.zip')
        if not archive.exists():
            print('Downloading ' + item['name'], flush=True)
            with urllib.request.urlopen(item['url'], timeout=120) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest() != item['sha256']:
                raise RuntimeError('Play tool checksum mismatch: ' + item['name'])
            archive.write_bytes(data)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != item['sha256']:
            raise RuntimeError('Cached Play tool checksum mismatch: ' + item['name'])
        if not destination.exists():
            destination.parent.mkdir(parents=True, exist_ok=True)
            if item['name'] == 'bundletool':
                shutil.copyfile(archive, destination)
            elif item['name'] == 'aapt2':
                destination.mkdir()
                with zipfile.ZipFile(archive) as package:
                    package.extractall(destination)
                (destination / 'aapt2').chmod(0o755)
            else:
                with tempfile.TemporaryDirectory(dir=cache) as temporary:
                    with zipfile.ZipFile(archive) as package:
                        package.extractall(temporary)
                    child, = Path(temporary).iterdir()
                    shutil.move(str(child), destination)
                if item['name'].startswith('build-tools'):
                    for file in destination.iterdir():
                        if file.is_file() and not file.suffix:
                            file.chmod(0o755)
        for relative, expected in item['files'].items():
            file = destination if relative == '.' else destination / relative
            if hashlib.sha256(file.read_bytes()).hexdigest() != expected:
                raise RuntimeError('Installed Play tool changed: ' + str(file))
    print('Pinned Play build kit ready.', flush=True)


if __name__ == '__main__':
    main()
