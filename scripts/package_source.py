#!/usr/bin/env python3
"""Create the same sanitized, complete source for ZIP downloads and GitHub."""
from pathlib import Path
import argparse
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DOCS = ('README.md', 'INSTALLATION.md', 'FLOW.md', 'ARCHITECTURE.md',
        'DEPLOYMENT.md', 'TESTING.md', 'VERSIONING.md', 'GITHUB.md',
        'AGENTS.md', 'CHANGELOG.md')
ROOT_FILES = (*DOCS, '.gitignore', 'THIRD_PARTY_NOTICES.txt', 'DEVICE_TESTS.json',
              'SIGNING_IDENTITY.json', 'android-dependencies.json', 'components.json',
              'package.json', 'pnpm-lock.yaml', 'pnpm-workspace.yaml', 'tsconfig.json',
              'vite.config.ts', 'drizzle.config.ts', 'next.config.ts', 'next-env.d.ts',
              'cloudflare-env.d.ts', 'postcss.config.mjs', 'eslint.config.mjs',
              'LICENSE', 'LICENSE.md', 'LICENSE.txt')
DIRECTORIES = ('android', 'scripts', 'app', 'lib', 'worker', 'db', 'drizzle',
               'web-test', 'vendor', 'components', 'hooks', 'examples')
PRIVATE_PARTS = {'.git', '.local', 'build', 'node_modules', '__pycache__', '.wrangler',
                 '.sites-runtime', 'dist', 'outputs'}
PRIVATE_SUFFIXES = {'.p12', '.password', '.jks', '.keystore', '.pem', '.key', '.apk',
                    '.zip', '.pyc'}
PRIVATE_CONTENT = re.compile(
    rb'libfile_[A-Za-z0-9]{16,}|-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----|'
    rb'github_pat_[A-Za-z0-9_]{40,}|gh[pousr]_[A-Za-z0-9]{30,}|'
    rb'sk-(?:proj-|svcacct-)[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16}')


def source_files():
    missing = [name for name in DOCS if not (ROOT / name).is_file()]
    if missing:
        raise RuntimeError('Missing documentation: ' + ', '.join(missing))
    files = {ROOT / name for name in ROOT_FILES if (ROOT / name).is_file()}
    files.add(ROOT / '.openai/hosting.json')
    for directory in DIRECTORIES:
        files.update(p for p in (ROOT / directory).rglob('*') if p.is_file()
                     and not PRIVATE_PARTS.intersection(p.relative_to(ROOT).parts)
                     and p.suffix not in PRIVATE_SUFFIXES)
    public = ROOT / 'public'
    metadata = {'_headers', 'builds.json', 'release.properties', 'SHA256SUMS.txt',
                'test-results.txt', 'THIRD_PARTY_NOTICES.txt'}
    files.update(p for p in public.iterdir() if p.is_file()
                 and (p.suffix in ('.html', '.css', '.mjs', '.js', '.svg')
                      or p.name in metadata))
    return sorted(files)


def source_bytes(file):
    relative = file.relative_to(ROOT)
    if (file.is_symlink() or PRIVATE_PARTS.intersection(relative.parts)
            or file.suffix in PRIVATE_SUFFIXES
            or file.name.startswith(('.env', '.dev.vars'))):
        raise RuntimeError('Private/generated source path: ' + str(relative))
    body = file.read_bytes()
    if relative.as_posix() == 'SIGNING_IDENTITY.json':
        identity = json.loads(body)
        public_fields = ('package_name', 'certificate_sha256', 'restore_directory',
                         'key_alias', 'note')
        body = (json.dumps({k: identity[k] for k in public_fields if k in identity},
                           indent=2) + '\n').encode()
    elif relative.as_posix() == '.openai/hosting.json':
        hosting = json.loads(body)
        hosting.pop('project_id', None)
        body = (json.dumps(hosting, indent=2) + '\n').encode()
    elif relative.as_posix() == '.gitignore':
        text = body.decode()
        rules = ('public/apks/', 'public/SquashTap-source.zip',
                 '.openai/deliverables.json', '*.pem', '*.key')
        extra = [rule for rule in rules if rule not in text.splitlines()]
        if extra:
            text += '\n# Source-only public export; official release artifacts stay separate.\n'
            text += '\n'.join(extra) + '\n'
        body = text.encode()
    if PRIVATE_CONTENT.search(body):
        raise RuntimeError('Private reference/credential pattern in source: ' + str(relative))
    return body


def package(github_directory=None):
    target = None
    if github_directory is not None:
        target = Path(github_directory).resolve()
        if target == ROOT or ROOT in target.parents:
            raise RuntimeError('Use an export directory outside the source checkout')
        if target.exists() and (not target.is_dir() or any(target.iterdir())):
            raise RuntimeError('GitHub export directory must be empty')
    payload = [(file, source_bytes(file)) for file in source_files()]
    destination = ROOT / 'public/SquashTap-source.zip'
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
        for file, body in payload:
            entry = zipfile.ZipInfo('SquashTap/' + file.relative_to(ROOT).as_posix(),
                                    (2026, 11, 3, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            mode = 0o755 if file.stat().st_mode & 0o111 else 0o644
            entry.external_attr = mode << 16
            archive.writestr(entry, body)
    if target is not None:
        target.mkdir(parents=True, exist_ok=True)
        for file, body in payload:
            output = target / file.relative_to(ROOT)
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(body)
            output.chmod(0o755 if file.stat().st_mode & 0o111 else 0o644)
        print('GitHub source:', target, '(' + str(len(payload)) + ' files)')
    print('Source download:', destination, '(' + str(len(payload)) + ' files)')
    return destination


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--github-dir', type=Path,
                        help='Also write the sanitized source into an empty external directory')
    package(parser.parse_args().github_dir)
