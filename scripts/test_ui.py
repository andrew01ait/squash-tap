#!/usr/bin/env python3
"""Run Android navigation/style tests with Robolectric and the APK's AAPT resources."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,urllib.parse,urllib.request,zipfile,xml.sax.saxutils
from androidx_deps import prepare

ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT/'.local'

def verified_download(url,destination,algorithm='sha1'):
    destination.parent.mkdir(parents=True,exist_ok=True)
    if destination.exists():return
    with urllib.request.urlopen(url+'.'+algorithm,timeout=30) as response:expected=response.read().decode().split()[0]
    with urllib.request.urlopen(url,timeout=120) as response:data=response.read()
    if hashlib.new(algorithm,data).hexdigest()!=expected:raise RuntimeError('Build-tool checksum mismatch')
    destination.write_bytes(data)

def dependencies():
    output=CACHE/'ui-dependencies'
    if not (output/'robolectric-4.14.1.jar').exists():
        maven=CACHE/'apache-maven-3.9.9'
        archive=CACHE/'apache-maven-3.9.9-bin.zip'
        verified_download('https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip',archive,'sha512')
        if not maven.exists():
            with zipfile.ZipFile(archive) as bundle:bundle.extractall(CACHE)
        (maven/'bin/mvn').chmod(0o755)
        args=[str(maven/'bin/mvn'),'-B','-f',str(ROOT/'android/ui-test/pom.xml'),'-Dmaven.repo.local='+str(CACHE/'ui-maven'),
            'org.apache.maven.plugins:maven-dependency-plugin:3.7.1:copy-dependencies','-DoutputDirectory='+str(output)]
        proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy') or os.environ.get('HTTP_PROXY') or '')
        descriptor=None
        if proxy.hostname:
            escape=xml.sax.saxutils.escape;credentials=''
            if proxy.username:credentials+='<username>'+escape(urllib.parse.unquote(proxy.username))+'</username>'
            if proxy.password:credentials+='<password>'+escape(urllib.parse.unquote(proxy.password))+'</password>'
            settings='<settings><proxies>'+''.join('<proxy><id>build-'+protocol+'</id><active>true</active><protocol>'+protocol+'</protocol><host>'+escape(proxy.hostname)+'</host><port>'+str(proxy.port or 80)+'</port>'+credentials+'</proxy>' for protocol in ('http','https'))+'</proxies></settings>'
            descriptor=os.memfd_create('ui-build-settings',0);os.write(descriptor,settings.encode());os.lseek(descriptor,0,0)
            args[1:1]=['-s','/proc/self/fd/'+str(descriptor)]
        try:
            with (CACHE/'ui-dependency-setup.log').open('w') as log:
                subprocess.run(args,cwd=ROOT,pass_fds=(() if descriptor is None else (descriptor,)),stdout=log,stderr=subprocess.STDOUT,check=True)
        finally:
            if descriptor is not None:os.close(descriptor)
    libraries=sorted(output.glob('*.jar'))
    for archive in sorted(output.glob('*.aar')):
        expanded=CACHE/'ui-aar'/archive.stem
        if not expanded.exists():
            with zipfile.ZipFile(archive) as bundle:bundle.extractall(expanded)
        libraries.append(expanded/'classes.jar')
    return libraries

def run(version, play=False):
    libraries=dependencies()
    sdk=CACHE/'ui-sdk';sdk.mkdir(exist_ok=True)
    sdk_files=[]
    # API 34 executes the tests; Robolectric 4.14 uses API 35 to parse compiled resources.
    for sdk_version in ('14-robolectric-10818077-i7','15-robolectric-12650502-i7'):
        sdk_name='android-all-instrumented-'+sdk_version+'.jar'
        verified_download('https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/'+sdk_version+'/'+sdk_name,sdk/sdk_name)
        sdk_files.append(sdk/sdk_name)
    app_libraries,_,_=prepare()
    work=ROOT/'build'/version;generated=work/'generated'
    if not (work/'unsigned.apk').exists():raise RuntimeError('Build the release before UI tests')
    app_classes=CACHE/('ui-play-app-classes' if play else 'ui-app-classes');shutil.rmtree(app_classes,ignore_errors=True);app_classes.mkdir()
    test_classes=CACHE/('ui-play-test-classes' if play else 'ui-test-classes');shutil.rmtree(test_classes,ignore_errors=True);test_classes.mkdir()
    cp=os.pathsep.join(str(p) for p in [*libraries,*app_libraries])
    def command(args):subprocess.run([str(arg) for arg in args],cwd=ROOT,check=True)
    from build_play import app_sources
    command(['java','com.sun.tools.javac.Main','--release','8','-classpath',cp,'-d',app_classes,*app_sources(play),*sorted(generated.rglob('*.java'))])
    cp=str(app_classes)+os.pathsep+cp
    tests=sorted((ROOT/'android/ui-test').rglob('*Test.java'))
    if play:tests+=sorted((ROOT/'android/play-test').rglob('*Test.java'))
    command(['java','com.sun.tools.javac.Main','--release','8','-classpath',cp,'-d',test_classes,*tests])
    config=test_classes/'com/android/tools/test_config.properties';config.parent.mkdir(parents=True,exist_ok=True)
    config.write_text('android_resource_apk='+str(work/'unsigned.apk')+'\nandroid_merged_manifest='+str(work/'AndroidManifest.xml' if play else ROOT/'android/AndroidManifest.xml')+'\nandroid_custom_package=at.andrew.squashtap\n')
    result=subprocess.run(['java','-Xmx1024m','-Djava.awt.headless=true','-Drobolectric.offline=true','-Drobolectric.dependency.dir='+str(sdk),'-cp',str(test_classes)+os.pathsep+cp,
        'org.junit.runner.JUnitCore',*('at.andrew.squashtap.'+test.stem for test in tests)],cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    (work/'ui-test-results.txt').write_text(result.stdout);print(result.stdout,flush=True)
    if result.returncode:raise SystemExit(result.returncode)
    inventory={p.relative_to(CACHE).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in [*libraries,*sdk_files]}
    (CACHE/'ui-dependency-checksums.json').write_text(json.dumps(inventory,indent=2)+'\n')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--version',default='0.9.0');parser.add_argument('--play',action='store_true');args=parser.parse_args();run(args.version,args.play)
