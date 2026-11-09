#!/usr/bin/env python3
"""Restore the build kit from official Google and Maven repositories."""
from pathlib import Path
import hashlib,urllib.request,xml.etree.ElementTree as ET,zipfile
ROOT=Path(__file__).resolve().parents[1]
KIT=ROOT/'.local/android-sdk'
def fetch(url):
    with urllib.request.urlopen(url,timeout=90) as response:return response.read()
def main():
    KIT.mkdir(parents=True,exist_ok=True)
    xml=ET.fromstring(fetch('https://dl.google.com/android/repository/repository2-1.xml'))
    for package_path in ['build-tools;34.0.0','platforms;android-34']:
        package=next(p for p in xml.iter('remotePackage') if p.get('path')==package_path)
        archive=next(a.find('complete') for a in package.findall('archives/archive') if a.findtext('host-os') in [None,'linux'])
        url=archive.findtext('url');expected=archive.findtext('checksum').strip();algorithm=archive.find('checksum').get('type','sha1')
        destination=KIT/package_path.split(';')[0]/package_path.split(';')[1]
        if destination.exists():continue
        print('Downloading '+package_path,flush=True);data=fetch('https://dl.google.com/android/repository/'+url)
        if hashlib.new(algorithm,data).hexdigest()!=expected:raise RuntimeError('SDK checksum mismatch')
        temporary=KIT/'archive.zip';temporary.write_bytes(data)
        import tempfile,shutil
        with tempfile.TemporaryDirectory(dir=KIT) as expanded:
            with zipfile.ZipFile(temporary) as bundle:bundle.extractall(expanded)
            child=next(Path(expanded).iterdir());destination.parent.mkdir(exist_ok=True);shutil.move(str(child),destination)
        temporary.unlink()
        if package_path.startswith('build-tools'):
            for file in destination.iterdir():
                if file.is_file() and not file.suffix:file.chmod(0o755)
    for group,name,version in [('junit','junit','4.13.2'),('org/hamcrest','hamcrest-core','1.3')]:
        target=KIT/(name+'.jar');url='https://repo.maven.apache.org/maven2/'+group+'/'+name+'/'+version+'/'+name+'-'+version+'.jar'
        if target.exists():continue
        data=fetch(url);expected=fetch(url+'.sha1').decode().split()[0]
        if hashlib.sha1(data).hexdigest()!=expected:raise RuntimeError('Test dependency checksum mismatch')
        target.write_bytes(data)
    print('Android build kit ready: '+str(KIT),flush=True)
if __name__=='__main__':main()
