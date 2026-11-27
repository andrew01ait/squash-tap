#!/usr/bin/env python3
"""Compile and test the current Wear OS release and produce an aligned APK."""
from pathlib import Path
import argparse, os, subprocess, sys, zipfile

ROOT=Path(__file__).resolve().parents[1]
PKG='at.andrew.squashtap'
SITE='https://squash-tap-andrew.endruuu.chatgpt.site'
RELEASES=((43,'0.23.4'),)
RELEASE_NOTES='QR screens leave room for scanning. Share with watches separates pairing and connected devices. After discarding a match, back returns through sport selection to Home.'

def run(args, env=None):
    result=subprocess.run([str(x) for x in args],cwd=ROOT,env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    print(result.stdout.strip(),flush=True)
    if result.returncode: raise SystemExit(f'Failed: {args[0]} (exit {result.returncode})')
    return result.stdout

def build(sdk,junit,hamcrest):
    tools=next((p for p in (sdk/'build-tools/34.0.0',sdk/'android-14') if (p/'aapt2').exists()),None)
    platform=next((p for p in (sdk/'platforms/android-34/android.jar',sdk/'android-34/android.jar') if p.exists()),None)
    if tools is None or platform is None: raise SystemExit('Android Build Tools 34 and platform 34 are required. See DEPLOYMENT.md.')
    env=os.environ.copy()
    if 'JAVA_HOME' not in env:
        jvm=Path('/usr/lib/jvm/java-17-openjdk-amd64')
        if jvm.exists(): env['JAVA_HOME']=str(jvm)
    work=ROOT/'build';work.mkdir(exist_ok=True)
    test_classes=work/'test-classes';test_classes.mkdir(exist_ok=True)
    core=[ROOT/'android/src/at/andrew/squashtap'/f'{name}.java' for name in ('SharedResult','HeadToHead','ScorePalette','BackupArchive','MatchPresets','MatchReminders','PickleballGame','ServiceOrder','MatchFormat','ScoreState','TouchTargets','UpdatePolicy','VerifiedDownload','MatchState','MatchResult','MatchStatistics','ManualMatchEntry','MatchBook','MatchDisplayPolicy','DiagnosticTransfer','OpponentDirectory','ResultFilter','MatchCsv','TransferCrypto')]
    tests=sorted((ROOT/'android/test').rglob('*Test.java'))
    cp=os.pathsep.join((str(junit),str(hamcrest)))
    print('Compiling and running local JUnit tests',flush=True)
    run(['java','com.sun.tools.javac.Main','--release','8','-classpath',cp,'-d',test_classes,*core,*tests],env)
    result=run(['java','-cp',str(test_classes)+os.pathsep+cp,'org.junit.runner.JUnitCore',*('at.andrew.squashtap.'+t.stem for t in tests)],env)
    (work/'test-results.txt').write_text(result)
    print('Running browser interchange and relay/storage tests',flush=True)
    web_result=run(['node','--test',*sorted((ROOT/'web-test').glob('*.test.mjs'))],env)
    (work/'web-test-results.txt').write_text(web_result)
    from androidx_deps import prepare
    libraries,library_resources,library_packages=prepare()
    for code,version in RELEASES:
        print(f'Building Keep Score {version}',flush=True)
        out=work/version;out.mkdir(exist_ok=True)
        classes=out/'classes';classes.mkdir(exist_ok=True)
        dex=out/'dex';dex.mkdir(exist_ok=True)
        config=out/'generated/at/andrew/squashtap/BuildConfig.java';config.parent.mkdir(parents=True,exist_ok=True)
        config.write_text(f'package {PKG}; public final class BuildConfig {{ public static final String VERSION_NAME="{version}"; public static final int VERSION_CODE={code}; public static final String SITE_URL="{SITE}"; public static final String RELEASE_URL="{SITE}/release.properties"; public static final boolean PLAY_DISTRIBUTION=false; }}\n')
        resource_archives=[]
        for index,resources in enumerate([*library_resources,ROOT/'android/res']):
            destination=out/f'resources-{index}.zip'
            run([tools/'aapt2','compile','--dir',resources,'-o',destination],env);resource_archives.append(destination)
        link=[tools/'aapt2','link','--auto-add-overlay','--manifest',ROOT/'android/AndroidManifest.xml','--version-code',str(code),'--version-name',version,'-I',platform,'--java',out/'generated','--extra-packages',':'.join(library_packages),'-o',out/'unsigned.apk']
        for resource in resource_archives:link.extend(['-R',resource])
        run(link,env)
        classpath=os.pathsep.join(str(p) for p in [platform,*libraries])
        run(['java','com.sun.tools.javac.Main','--release','8','-classpath',classpath,'-d',classes,*sorted((ROOT/'android/src').rglob('*.java')),*sorted((out/'generated').rglob('*.java'))],env)
        print('Running Android navigation, session and power tests with the compiled resources',flush=True)
        run(['python3',ROOT/'scripts/test_ui.py','--version',version],env)
        run([tools/'d8','--lib',platform,'--min-api','30','--output',dex,*sorted(classes.rglob('*.class')),*libraries],env)
        with zipfile.ZipFile(out/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as archive:
            for file in dex.glob('*.dex'):archive.write(file,file.name)
            archive.write(ROOT/'THIRD_PARTY_NOTICES.txt','assets/THIRD_PARTY_NOTICES.txt')
        run([tools/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk'],env)
        print(f"Aligned unsigned watch APK: {out / 'aligned.apk'}",flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk',type=Path,required=True)
    parser.add_argument('--junit',type=Path,required=True)
    parser.add_argument('--hamcrest',type=Path,required=True)
    parser.add_argument('--unsigned',action='store_true',help='Finish with the aligned development APK.')
    args=parser.parse_args()
    build(args.sdk,args.junit,args.hamcrest)
    distribution=ROOT/'maintainer'/'official_build.py'
    if distribution.is_file() and not args.unsigned:
        subprocess.run([sys.executable,str(distribution),'--sdk',str(args.sdk.resolve())],cwd=ROOT,check=True)
