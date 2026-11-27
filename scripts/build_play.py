#!/usr/bin/env python3
"""Build and validate a preparation-only Play AAB; never update public APK feeds."""
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, subprocess, sys, xml.etree.ElementTree as ET, zipfile
from androidx_deps import prepare

ROOT = Path(__file__).resolve().parents[1]
PKG = 'at.andrew.squashtap'
SITE = 'https://squash-tap-andrew.endruuu.chatgpt.site'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def run(args, quiet=False):
    result = subprocess.run([str(a) for a in args], cwd=ROOT, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.stdout.strip() and not quiet:
        print(result.stdout.strip(), flush=True)
    if result.returncode:
        if quiet:
            print(result.stdout, flush=True)
        raise RuntimeError('Command failed: ' + str(args[0]))
    return result.stdout


def generated_config(directory, version, code, play):
    config = directory / 'generated/at/andrew/squashtap/BuildConfig.java'
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text('package ' + PKG + '; public final class BuildConfig {'
        + 'public static final String VERSION_NAME="' + version + '";'
        + 'public static final int VERSION_CODE=' + str(code) + ';'
        + 'public static final String SITE_URL="' + SITE + '";'
        + 'public static final String RELEASE_URL="' + SITE + '/release.properties";'
        + 'public static final boolean PLAY_DISTRIBUTION=' + str(play).lower() + ';}\n')


def app_sources(play):
    sources = sorted((ROOT / 'android/src').rglob('*.java'))
    if play:
        sources = [p for p in sources if p.name not in ('UpdateClient.java', 'InstallReceiver.java')]
        sources += sorted((ROOT / 'android/play-src').rglob('*.java'))
    return sources


def write_manifest(path, config):
    ET.register_namespace('android', ANDROID[1:-1])
    manifest = ET.parse(ROOT / 'android/AndroidManifest.xml')
    top = manifest.getroot()
    sdk = top.find('uses-sdk')
    sdk.set(ANDROID + 'minSdkVersion', str(config['minSdk']))
    sdk.set(ANDROID + 'targetSdkVersion', str(config['targetSdk']))
    for permission in list(top.findall('uses-permission')):
        if permission.get(ANDROID + 'name') in (
                'android.permission.REQUEST_INSTALL_PACKAGES',
                'android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION'):
            top.remove(permission)
    application = top.find('application')
    application.set(ANDROID + 'theme', '@style/PlayAppTheme')
    for receiver in list(application.findall('receiver')):
        if receiver.get(ANDROID + 'name') == '.InstallReceiver':
            application.remove(receiver)
    manifest.write(path, encoding='utf-8', xml_declaration=True)


def retained_state():
    snapshots = {}
    for name in ('release.properties', 'builds.json', 'SHA256SUMS.txt', 'test-results.txt'):
        snapshots[name] = (ROOT / 'public' / name).read_bytes()
    for release in json.loads(snapshots['builds.json']):
        data = (ROOT / 'public/apks' / release['filename']).read_bytes()
        if len(data) != release['sizeBytes'] or hashlib.sha256(data).hexdigest() != release['sha256']:
            raise RuntimeError('Retained APK changed: ' + release['filename'])
    return snapshots


def build(skip_tests=False):
    before = retained_state()
    config = json.loads((ROOT / 'play-release.json').read_text())
    published = dict(line.split('=', 1) for line in before['release.properties'].decode().splitlines() if '=' in line)
    if config['packageName'] != PKG or config['versionCode'] <= int(published['versionCode']):
        raise RuntimeError('The candidate must keep the package and advance the published version code.')
    if config['targetSdk'] < 35 or config['minSdk'] != 30:
        raise RuntimeError('Invalid Play SDK configuration.')
    lock = json.loads((ROOT / 'play-build-tools.json').read_text())
    tools = {item['name']: ROOT / item['destination'] for item in lock['tools']}
    aapt = tools['aapt2'] / 'aapt2'
    sdk_tools = tools['build-tools;35.0.0']
    platform = tools['platforms;android-35'] / 'android.jar'
    bundletool = tools['bundletool']
    key = ROOT / '.local/signing/squash-tap.p12'
    password = ROOT / '.local/signing/key.password'
    if not key.is_file() or not password.is_file():
        raise RuntimeError('Restore the original signing material; never generate a replacement app key.')
    expected_signer = json.loads((ROOT / 'SIGNING_IDENTITY.json').read_text())['certificate_sha256']
    work = ROOT / 'build/play' / config['versionName']
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    manifest = work / 'AndroidManifest.xml'
    write_manifest(manifest, config)
    generated_config(work, config['versionName'], config['versionCode'], True)
    libraries, resources, packages = prepare()
    # Reject unhandled JNI assets instead of silently dropping native libraries.
    for item in json.loads((ROOT / 'android-dependencies.json').read_text())['artifacts']:
        path = ROOT / '.local/androidx' / item['group'].replace('.', '/') / item['name'] / item['version'] / (item['name'] + '-' + item['version'] + '.' + item['type'])
        with zipfile.ZipFile(path) as archive:
            if any(name.endswith('.so') for name in archive.namelist()):
                raise RuntimeError('Review JNI/64-bit packaging before using this dependency: ' + item['name'])
    archives = []
    for index, resource in enumerate([*resources, ROOT / 'android/res', ROOT / 'android/play-res']):
        output = work / ('resources-' + str(index) + '.zip')
        run([aapt, 'compile', '--dir', resource, '-o', output], quiet=True)
        archives.append(output)
    def link(destination, proto=False, source_manifest=manifest, java=True):
        args = [aapt, 'link', '--auto-add-overlay', '--manifest', source_manifest,
                '--version-code', str(config['versionCode']), '--version-name', config['versionName'],
                '-I', platform, '--extra-packages', ':'.join(packages), '-o', destination]
        if java:
            args += ['--java', work / 'generated']
        if proto:
            args += ['--proto-format']
        for archive in archives:
            args += ['-R', archive]
        run(args, quiet=True)
    link(work / 'unsigned.apk')
    classes = work / 'classes'
    dex = work / 'dex'
    classes.mkdir(); dex.mkdir()
    classpath = os.pathsep.join(str(p) for p in [platform, *libraries])
    run(['java', 'com.sun.tools.javac.Main', '--release', '8', '-classpath', classpath,
         '-d', classes, *app_sources(True), *sorted((work / 'generated').rglob('*.java'))])
    run([sdk_tools / 'd8', '--lib', platform, '--min-api', '30', '--output', dex,
         *sorted(classes.rglob('*.class')), *libraries], quiet=True)
    proto = work / 'resources-proto.apk'
    link(proto, proto=True, java=False)
    module = work / 'base.zip'
    with zipfile.ZipFile(module, 'w', zipfile.ZIP_DEFLATED) as output, zipfile.ZipFile(proto) as inputs:
        for name in inputs.namelist():
            if name == 'AndroidManifest.xml':
                output.writestr('manifest/AndroidManifest.xml', inputs.read(name))
            elif name == 'resources.pb' or name.startswith('res/'):
                output.writestr(name, inputs.read(name))
        for file in sorted(dex.glob('*.dex')):
            output.write(file, 'dex/' + file.name)
        output.write(ROOT / 'THIRD_PARTY_NOTICES.txt', 'assets/THIRD_PARTY_NOTICES.txt')
    bundle = work / ('KeepScore-' + config['versionName'] + '.aab')
    run(['java', '-jar', bundletool, 'build-bundle', '--modules=' + str(module), '--output=' + str(bundle)])
    run(['jarsigner', '-keystore', key, '-storetype', 'PKCS12', '-storepass:file', password,
         '-keypass:file', password, '-sigalg', 'SHA256withRSA', '-digestalg', 'SHA-256', bundle, 'squash-tap'], quiet=True)
    run(['jarsigner', '-verify', bundle], quiet=True)
    certificate = run(['keytool', '-printcert', '-jarfile', bundle], quiet=True)
    fingerprint = re.search(r'SHA256:\s*([0-9A-F:]+)', certificate)
    if fingerprint is None or fingerprint.group(1).replace(':', '').lower() != expected_signer:
        raise RuntimeError('Bundle signer differs from the installed app signer.')
    run(['java', '-jar', bundletool, 'validate', '--bundle=' + str(bundle)], quiet=True)
    apkset = work / 'candidate.apks'
    run(['java', '-jar', bundletool, 'build-apks', '--bundle=' + str(bundle),
         '--output=' + str(apkset), '--mode=universal', '--aapt2=' + str(aapt),
         '--ks=' + str(key), '--ks-key-alias=squash-tap', '--ks-pass=file:' + str(password),
         '--key-pass=file:' + str(password)], quiet=True)
    apk = work / ('KeepScore-' + config['versionName'] + '-test.apk')
    with zipfile.ZipFile(apkset) as archive:
        apk.write_bytes(archive.read('universal.apk'))
    signature = run([sdk_tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk], quiet=True)
    badging = run([aapt, 'dump', 'badging', apk], quiet=True)
    permissions = run([aapt, 'dump', 'permissions', apk], quiet=True)
    if expected_signer not in signature or ("name='" + PKG + "'") not in badging:
        raise RuntimeError('Candidate signing/package identity mismatch.')
    for required in ("versionCode='" + str(config['versionCode']) + "'",
                     "targetSdkVersion:'35'", "minSdkVersion:'30'", "name='android.hardware.type.watch'"):
        if required not in badging:
            raise RuntimeError('Candidate manifest mismatch: ' + required)
    if any(p in permissions for p in ('REQUEST_INSTALL_PACKAGES', 'UPDATE_PACKAGES_WITHOUT_USER_ACTION')):
        raise RuntimeError('Play candidate contains installer permissions.')
    with zipfile.ZipFile(apk) as archive:
        if any(name.startswith('lib/') and name.endswith('.so') for name in archive.namelist()):
            raise RuntimeError('Unexpected native library; review 64-bit packaging.')
        bytecode = b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
        if b'Landroid/content/pm/PackageInstaller;' in bytecode or b'Lat/andrew/squashtap/InstallReceiver;' in bytecode:
            raise RuntimeError('APK installer implementation leaked into the Play package.')
    run([sdk_tools / 'zipalign', '-c', '4', apk], quiet=True)
    if not skip_tests:
        run(['python3', ROOT / 'scripts/test_ui.py', '--version', 'play/' + config['versionName'], '--play'])
    for name, data in before.items():
        if (ROOT / 'public' / name).read_bytes() != data:
            raise RuntimeError('Public release metadata changed: ' + name)
    report = {'application': 'Keep Score', 'status': 'preparation-only', 'versionName': config['versionName'],
              'versionCode': config['versionCode'], 'targetSdk': config['targetSdk'],
              'packageName': PKG, 'appSignerSha256': expected_signer, 'bundleValid': True,
              'installerPermissionsAbsent': True, 'installerImplementationAbsent': True,
              'nativeLibraries': 0, 'retainedReleasesUnchanged': True, 'uiTestsRun': not skip_tests,
              'physicalUpgradeTested': False, 'playConsoleSubmitted': False,
              'artifacts': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (bundle, apk)}}
    (work / 'validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)
    return work


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--skip-tests', action='store_true', help='Assemble only; does not establish test acceptance.')
    build(parser.parse_args().skip_tests)
