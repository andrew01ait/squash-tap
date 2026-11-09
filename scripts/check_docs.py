#!/usr/bin/env python3
"""Validate the current guides, local links, release identity and export parity."""
from pathlib import Path
import argparse
import re
import sys
from urllib.parse import unquote, urlsplit
from package_source import DOCS

ROOT = Path(__file__).resolve().parents[1]


def check(compare=None):
    errors = []
    links = 0
    for name in DOCS:
        file = ROOT / name
        if not file.is_file():
            errors.append('Missing guide: ' + name)
            continue
        body = file.read_text()
        if not body.startswith('# '):
            errors.append('Missing guide title: ' + name)
        for target in re.findall(r'\[[^\]]*\]\(([^\s)]+)\)', body):
            url = urlsplit(target)
            if url.scheme or url.netloc or not url.path:
                continue
            destination = (file.parent / unquote(url.path)).resolve()
            links += 1
            if ROOT not in destination.parents or not destination.exists():
                errors.append(name + ': missing/nonportable link ' + target)
        if compare is not None:
            exported = Path(compare) / name
            if not exported.is_file() or exported.read_bytes() != file.read_bytes():
                errors.append('Documentation differs in export: ' + name)
    release = dict(line.split('=', 1) for line in
                   (ROOT / 'public/release.properties').read_text().splitlines() if '=' in line)
    for name in ('README.md', 'VERSIONING.md'):
        if (ROOT / name).is_file():
            body = (ROOT / name).read_text()
            if ('v' + release['versionName']) not in body or release['versionCode'] not in body:
                errors.append(name + ': current release identity is missing')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        raise SystemExit(1)
    print('Documentation OK:', len(DOCS), 'guides,', links, 'local links; watch v' +
          release['versionName'] + '/code ' + release['versionCode'] +
          ('; export parity verified' if compare is not None else ''))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compare', type=Path, help='Compare guides with a public source export')
    check(parser.parse_args().compare)
