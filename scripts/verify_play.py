#!/usr/bin/env python3
"""Verify both distributions and their shared scoring without publishing a release."""
from pathlib import Path
import json, os, re, shutil
from build_play import ROOT, PKG, run, generated_config, retained_state, build
from androidx_deps import prepare


def direct_ui_resources():
    work = ROOT / 'build/direct-regression'
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    release = dict(line.split('=', 1) for line in
                   (ROOT / 'public/release.properties').read_text().splitlines() if '=' in line)
    generated_config(work, release['versionName'], int(release['versionCode']), False)
    tools = ROOT / '.local/android-sdk/build-tools/34.0.0'
    platform = ROOT / '.local/android-sdk/platforms/android-34/android.jar'
    _, resources, packages = prepare()
    archives = []
    for index, resource in enumerate([*resources, ROOT / 'android/res']):
        output = work / ('resources-' + str(index) + '.zip')
        run([tools / 'aapt2', 'compile', '--dir', resource, '-o', output], quiet=True)
        archives.append(output)
    args = [tools / 'aapt2', 'link', '--auto-add-overlay', '--manifest', ROOT / 'android/AndroidManifest.xml',
            '--version-code', release['versionCode'], '--version-name', release['versionName'],
            '-I', platform, '--java', work / 'generated', '--extra-packages', ':'.join(packages),
            '-o', work / 'unsigned.apk']
    for archive in archives:
        args += ['-R', archive]
    run(args, quiet=True)


def count_junit(text):
    result = re.search(r'OK \((\d+) tests?\)', text)
    if result is None:
        raise RuntimeError('Missing successful JUnit report')
    return int(result.group(1))


def main():
    before = retained_state()
    work = ROOT / 'build'
    work.mkdir(exist_ok=True)
    # Browser/native golden-vector tests invoke this established classpath.
    classes = work / 'test-classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    names = ('HeadToHead','ScorePalette','BackupArchive','MatchPresets','MatchReminders','PickleballGame',
             'ServiceOrder','MatchFormat','ScoreState','TouchTargets','UpdatePolicy','VerifiedDownload',
             'MatchState','MatchResult','MatchStatistics','ManualMatchEntry','MatchBook','MatchDisplayPolicy',
             'DiagnosticTransfer','OpponentDirectory','ResultFilter','MatchCsv','TransferCrypto')
    core = [ROOT / 'android/src/at/andrew/squashtap' / (name + '.java') for name in names]
    tests = sorted((ROOT / 'android/test').rglob('*Test.java'))
    cp = os.pathsep.join(str(ROOT / '.local/android-sdk' / name) for name in ('junit.jar','hamcrest-core.jar'))
    run(['java','com.sun.tools.javac.Main','--release','8','-classpath',cp,'-d',classes,*core,*tests])
    core_report = run(['java','-cp',str(classes)+os.pathsep+cp,'org.junit.runner.JUnitCore',
                       *('at.andrew.squashtap.' + p.stem for p in tests)])
    (work / 'play-core-test-results.txt').write_text(core_report)
    web_report = run(['node','--test','--test-reporter=tap',*(ROOT / 'web-test' / (name + '.test.mjs') for name in
        ('interchange','transfer-state','relay','multisport','backup','live'))])
    (work / 'play-web-test-results.txt').write_text(web_report)
    web_count = re.search(r'# pass (\d+)', web_report)
    if not web_count:
        raise RuntimeError('Missing successful web test report')
    direct_ui_resources()
    run(['python3', ROOT / 'scripts/test_ui.py', '--version', 'direct-regression'])
    candidate = build()
    results = {
        'application': 'Keep Score',
        'candidate': json.loads((ROOT / 'play-release.json').read_text()),
        'core': count_junit(core_report),
        'nativeDirect': count_junit((work / 'direct-regression/ui-test-results.txt').read_text()),
        'nativePlay': count_junit((candidate / 'ui-test-results.txt').read_text()),
        'webRelay': int(web_count.group(1)),
        'physicalWatchAcceptance': 'pending',
        'playConsoleReview': 'not submitted'
    }
    if any((ROOT / 'public' / name).read_bytes() != data for name, data in before.items()):
        raise RuntimeError('Public release metadata changed during verification')
    output = work / 'play/verification.json'
    output.write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(results, indent=2), flush=True)


if __name__ == '__main__':
    main()
