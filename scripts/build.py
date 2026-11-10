#!/usr/bin/env python3
"""Build/test/sign the new release, retaining immutable historical APKs."""
from pathlib import Path
import argparse, hashlib, json, os, secrets, shutil, subprocess, sys, zipfile

ROOT=Path(__file__).resolve().parents[1]
PKG='at.andrew.squashtap'
SITE='https://squash-tap-andrew.endruuu.chatgpt.site'
RELEASES=((15,'0.14.0'),)
RELEASE_NOTES='Five sports: squash, tennis, badminton, padel and table tennis. Shared players with sport icons; separate histories and statistics. Singles/doubles, old/new rule presets, optional serving indicators, and compatible phone CSV transfers.'

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
    core=[ROOT/'android/src/at/andrew/squashtap'/f'{name}.java' for name in ('ServiceOrder','MatchFormat','ScoreState','TouchTargets','UpdatePolicy','VerifiedDownload','MatchState','MatchResult','MatchStatistics','ManualMatchEntry','MatchBook','MatchDisplayPolicy','DiagnosticTransfer','OpponentDirectory','ResultFilter','MatchCsv','TransferCrypto')]
    tests=sorted((ROOT/'android/test').rglob('*Test.java'))
    cp=os.pathsep.join((str(junit),str(hamcrest)))
    print('Compiling and running local JUnit tests',flush=True)
    run(['java','com.sun.tools.javac.Main','--release','8','-classpath',cp,'-d',test_classes,*core,*tests],env)
    result=run(['java','-cp',str(test_classes)+os.pathsep+cp,'org.junit.runner.JUnitCore',*('at.andrew.squashtap.'+t.stem for t in tests)],env)
    (work/'test-results.txt').write_text(result)
    print('Running browser interchange and relay/storage tests',flush=True)
    web_result=run(['node','--test',ROOT/'web-test/interchange.test.mjs',ROOT/'web-test/transfer-state.test.mjs',ROOT/'web-test/relay.test.mjs',ROOT/'web-test/multisport.test.mjs'],env)
    (work/'web-test-results.txt').write_text(web_result)
    signing=ROOT/'.local/signing';signing.mkdir(parents=True,exist_ok=True)
    key=signing/'squash-tap.p12';password=signing/'key.password'
    if not key.exists():
        if (ROOT/'SIGNING_IDENTITY.json').exists():
            raise SystemExit('Restore the existing private signing backup before building. See SIGNING_IDENTITY.json; a replacement key would break updates.')
        password.write_text(secrets.token_urlsafe(36));password.chmod(0o600)
        run(['keytool','-genkeypair','-storetype','PKCS12','-keystore',key,'-storepass:file',password,'-keypass:file',password,'-alias','squash-tap','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Squash Tap Personal Testing'],env)
        key.chmod(0o600)
    apks=ROOT/'public/apks';apks.mkdir(parents=True,exist_ok=True)
    from androidx_deps import prepare
    libraries,library_resources,library_packages=prepare()
    from release_retention import prune
    built=prune(ROOT)
    for previous in built:
        old_file=apks/previous['filename']
        if hashlib.sha256(old_file.read_bytes()).hexdigest()!=previous['sha256']:raise SystemExit('Retained APK changed: '+previous['filename'])
    for code,version in RELEASES:
        print(f'Building Squash Tap {version}',flush=True)
        out=work/version;out.mkdir(exist_ok=True)
        classes=out/'classes';classes.mkdir(exist_ok=True)
        dex=out/'dex';dex.mkdir(exist_ok=True)
        config=out/'generated/at/andrew/squashtap/BuildConfig.java';config.parent.mkdir(parents=True,exist_ok=True)
        config.write_text(f'package {PKG}; public final class BuildConfig {{ public static final String VERSION_NAME="{version}"; public static final int VERSION_CODE={code}; public static final String SITE_URL="{SITE}"; public static final String RELEASE_URL="{SITE}/release.properties"; }}\n')
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
        ui_result=(out/'ui-test-results.txt').read_text()
        run([tools/'d8','--lib',platform,'--min-api','30','--output',dex,*sorted(classes.rglob('*.class')),*libraries],env)
        with zipfile.ZipFile(out/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as archive:
            for file in dex.glob('*.dex'):archive.write(file,file.name)
            archive.write(ROOT/'THIRD_PARTY_NOTICES.txt','assets/THIRD_PARTY_NOTICES.txt')
        run([tools/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk'],env)
        apk=apks/f'SquashTap-{version}.apk'
        run([tools/'apksigner','sign','--ks',key,'--ks-key-alias','squash-tap','--ks-pass','file:'+str(password),'--out',apk,out/'aligned.apk'],env)
        signature=run([tools/'apksigner','verify','--verbose','--print-certs',apk],env)
        import re
        identity=json.loads((ROOT/'SIGNING_IDENTITY.json').read_text())
        if identity['certificate_sha256'] not in signature:raise SystemExit('Release signer differs from the installed app signer')
        (out/'signature.txt').write_text(signature)
        badging=run([tools/'aapt2','dump','badging',apk],env)
        (out/'manifest.txt').write_text(badging)
        if f"name='{PKG}'" not in badging or f"versionCode='{code}'" not in badging or "name='android.hardware.type.watch'" not in badging:raise SystemExit('APK metadata check failed')
        run([tools/'zipalign','-c','4',apk],env)
        built=[item for item in built if item['versionCode']!=code]
        built.append({'versionCode':code,'versionName':version,'filename':apk.name,'sizeBytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest()})
    built.sort(key=lambda item:item['versionCode'])
    latest=built[-1]
    release='schema=1\npackageName='+PKG+'\nversionCode='+str(latest['versionCode'])+'\nversionName='+latest['versionName']+'\nminSdk=30\nsizeBytes='+str(latest['sizeBytes'])+'\napkUrl='+SITE+'/apks/'+latest['filename']+'\nsha256='+latest['sha256']+'\nnotes='+RELEASE_NOTES+'\n'
    (ROOT/'public/release.properties').write_text(release)
    (ROOT/'public/builds.json').write_text(json.dumps(built,indent=2)+'\n')
    (ROOT/'public/SHA256SUMS.txt').write_text(''.join(x['sha256']+'  '+x['filename']+'\n' for x in built))
    device_notes='No device results have been recorded.\n'
    report_path=ROOT/'DEVICE_TESTS.json'
    if report_path.exists():
        report=json.loads(report_path.read_text())
        transition=report['tested_update']['from']+' to '+report['tested_update']['to']
        device_notes='Historical device report ('+report['date']+', '+report['device']+', update '+transition+'). These checks are user-reported and are not rerun by this build.\n'
        device_notes+='\n'.join('Confirmed: '+item for item in report['passed'])+'\n'
        device_notes+='\n'.join('Pending: '+item for item in report['pending'])+'\n'
        for follow_up in report.get('follow_up_reports',[]):
            device_notes+='User report '+follow_up['date']+' (v'+follow_up['version']+'): '+follow_up['observation']+'\n'
    (ROOT/'public/test-results.txt').write_text(result+'\nAndroid navigation/session/power checks (Robolectric, API 34, compiled AAPT resources):\n'+ui_result+'\nBrowser interchange and relay/storage checks (Node Web Crypto and Miniflare D1/R2):\n'+web_result+'\nNew release APK signature, manifest identity and ZIP alignment: verified. Retained APK hashes verified; public history starts at v0.3.0.\n'+device_notes+'No Wear OS emulator run is claimed. The user reports working foreground retention since v0.3 and successful v0.4 QR export. v'+latest['versionName']+' appearance, speech availability, phone file picking/QR scan, detailed ambient transitions and real battery consumption require physical validation. Battery snapshots reflect the whole watch, not attributed app consumption.\n')
    print(json.dumps({'builds':built,'tests':result.strip().splitlines()[-1],'uiTests':ui_result.strip().splitlines()[-1]},indent=2),flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--sdk',type=Path,required=True);parser.add_argument('--junit',type=Path,required=True);parser.add_argument('--hamcrest',type=Path,required=True)
    args=parser.parse_args();build(args.sdk,args.junit,args.hamcrest)
