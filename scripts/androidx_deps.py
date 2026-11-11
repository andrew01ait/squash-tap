#!/usr/bin/env python3
"""Resolve pinned AndroidX runtime artifacts from official Maven repositories."""
from pathlib import Path
import hashlib,json,re,urllib.request,xml.etree.ElementTree as ET,zipfile

ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT/'.local/androidx'
NS={'m':'http://maven.apache.org/POM/4.0.0'}

def download(group,name,version,extension):
    relative=group.replace('.','/')+'/'+name+'/'+version+'/'+name+'-'+version+'.'+extension
    origin='https://dl.google.com/dl/android/maven2/' if group.startswith('androidx.') else 'https://repo.maven.apache.org/maven2/'
    path=CACHE/relative;path.parent.mkdir(parents=True,exist_ok=True)
    if not path.exists():
        with urllib.request.urlopen(origin+relative,timeout=30) as response:path.write_bytes(response.read())
    return path

def version_key(value):
    # A stable 1.0.0 is newer than 1.0.0-beta01, even though its tuple is shorter.
    match=re.fullmatch(r'(\d+(?:\.\d+)*)(?:-(.*))?',value)
    if not match:raise RuntimeError('Unsupported dependency version: '+value)
    numbers=tuple(map(int,match.group(1).split('.')));numbers=numbers+(0,)*(4-len(numbers))
    suffix=match.group(2) or '';pre=re.fullmatch(r'(alpha|beta|rc)(\d+)',suffix)
    return numbers+(({'alpha':0,'beta':1,'rc':2}[pre.group(1)],int(pre.group(2))) if pre else (3 if suffix else 4,0))

def resolve():
    CACHE.mkdir(parents=True,exist_ok=True)
    queue=[('androidx.wear','wear-ongoing','1.1.0','aar'),('androidx.wear','wear','1.3.0','aar'),('com.google.zxing','core','3.5.3','jar'),('androidx.wear.tiles','tiles','1.2.0','aar'),('androidx.wear.watchface','watchface-complications-data-source','1.2.1','aar'),('org.jetbrains.kotlin','kotlin-stdlib','1.8.22','jar')]
    selected={};properties={}
    while queue:
        group,name,version,kind=queue.pop(0)
        version=version.strip('[]()').split(',')[0]
        key=group+':'+name
        if key in selected and version_key(selected[key]['version'])>=version_key(version):continue
        if len(selected)>120:raise RuntimeError('Unexpected dependency graph size')
        print('Resolving',key,version,flush=True)
        pom=ET.parse(download(group,name,version,'pom')).getroot()
        props={x.tag.split('}')[-1]:(x.text or '') for x in pom.findall('m:properties/*',NS)}
        props.update({'project.version':version,'pom.version':version})
        parent=pom.find('m:parent',NS)
        if parent is not None:
            pg=parent.findtext('m:groupId',namespaces=NS);pn=parent.findtext('m:artifactId',namespaces=NS);pv=parent.findtext('m:version',namespaces=NS)
            parent_pom=ET.parse(download(pg,pn,pv,'pom')).getroot()
            for item in parent_pom.findall('m:properties/*',NS):props.setdefault(item.tag.split('}')[-1],item.text or '')
        def value(text):
            if text is None:return None
            for _ in range(4):
                text=re.sub(r'\$\{([^}]+)\}',lambda m:props.get(m.group(1),m.group(0)),text)
            if '${' in text:raise RuntimeError('Unresolved Maven version property: '+text)
            return text
        actual=pom.findtext('m:packaging',default=kind,namespaces=NS)
        if actual not in ('jar','aar'):actual=kind
        archive=download(group,name,version,actual)
        selected[key]={'group':group,'name':name,'version':version,'type':actual,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()}
        for dep in pom.findall('m:dependencies/m:dependency',NS):
            if dep.findtext('m:optional',default='false',namespaces=NS)=='true':continue
            if dep.findtext('m:scope',default='compile',namespaces=NS) not in ('compile','runtime'):continue
            dg=dep.findtext('m:groupId',namespaces=NS);dn=dep.findtext('m:artifactId',namespaces=NS);dv=value(dep.findtext('m:version',namespaces=NS));dt=dep.findtext('m:type',default='jar',namespaces=NS)
            if dv is None and dg=='org.jetbrains.kotlin' and dn=='kotlin-stdlib':dv='1.8.22' # explicit project root
            if dv is None:
                # Some Google Maven POMs intentionally omit Kotlin versions and
                # publish the authoritative constraint in Gradle module metadata.
                module=json.loads(download(group,name,version,'module').read_text())
                constraints=[]
                for variant in module.get('variants',[]):
                    for dependency in variant.get('dependencies',[]):
                        if dependency.get('group')==dg and dependency.get('module')==dn:
                            constraint=dependency.get('version',{})
                            pinned=constraint.get('strictly') or constraint.get('requires') or constraint.get('prefers')
                            if pinned:constraints.append(pinned)
                if not constraints:raise RuntimeError('Dependency version missing from POM/module: '+dg+':'+dn)
                dv=max(constraints,key=version_key)
            queue.append((dg,dn,dv,dt))
    lock={'roots':['androidx.wear:wear-ongoing:1.1.0','androidx.wear:wear:1.3.0','com.google.zxing:core:3.5.3','androidx.wear.tiles:tiles:1.2.0','androidx.wear.watchface:watchface-complications-data-source:1.2.1','org.jetbrains.kotlin:kotlin-stdlib:1.8.22'],'artifacts':sorted(selected.values(),key=lambda x:(x['group'],x['name']))}
    (ROOT/'android-dependencies.json').write_text(json.dumps(lock,indent=2)+'\n')
    print('Pinned',len(selected),'runtime artifacts.',flush=True)

def prepare():
    lock=json.loads((ROOT/'android-dependencies.json').read_text());libraries=[];resources=[];packages=[]
    for item in lock['artifacts']:
        archive=download(item['group'],item['name'],item['version'],item['type'])
        if hashlib.sha256(archive.read_bytes()).hexdigest()!=item['sha256']:raise RuntimeError('Dependency checksum mismatch: '+item['name'])
        if item['type']=='jar':libraries.append(archive);continue
        expanded=archive.parent/'expanded'
        if not expanded.exists():
            with zipfile.ZipFile(archive) as bundle:bundle.extractall(expanded)
        if (expanded/'classes.jar').exists():libraries.append(expanded/'classes.jar')
        libraries.extend(sorted((expanded/'libs').glob('*.jar')) if (expanded/'libs').exists() else [])
        if (expanded/'res').exists() and any((expanded/'res').rglob('*')):resources.append(expanded/'res')
        if (expanded/'AndroidManifest.xml').exists():
            package=ET.parse(expanded/'AndroidManifest.xml').getroot().get('package')
            if package:packages.append(package)
    return libraries,resources,packages

if __name__=='__main__':resolve()
