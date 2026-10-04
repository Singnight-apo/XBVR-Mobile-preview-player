"""Collect legal evidence from resolved artifacts; inputs stay local, outputs omit paths.

Run the compliance Gradle init task first, then this script using Python 3.
No SDK, signing key, private references or server data are copied.
"""
import concurrent.futures
import hashlib
import gzip
import io
import json
import pathlib
import re
import struct
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
LEGAL = ROOT / 'licenses'
CACHE = ROOT / 'validation/compliance-sources'
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
PIN = '73170c345e6a762fc6a1f0301bb15218850023ef'

def get(url):
    with urllib.request.urlopen(url, timeout=90) as response:
        return response.read()

def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data if isinstance(data, bytes) else data.encode('utf-8'))

def sha(data):
    return hashlib.sha256(data).hexdigest()

def nested_notices(data):
    result = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            if name.endswith('/'):
                continue
            if re.search(r'(^|/)(license[^/]*|notice[^/]*|copying[^/]*)$', name, re.I):
                result.append((name, archive.read(name)))
            if name == 'classes.jar':
                result += [('classes.jar/' + n, b) for n,b in nested_notices(archive.read(name))]
    return result

def collect(row):
    coord = row['coordinate']
    group, artifact, version = coord.split(':')
    source = pathlib.Path(row['file'])
    if not source.is_file() or group == 'XBVR Pocket':
        return None
    if not any(s != 'debugUnitTestRuntimeClasspath' for s in row['scopes']):
        return None  # Test-only license files are maintained separately.
    data = source.read_bytes()
    pom_files = list(source.parent.parent.glob('*/*.pom'))
    pom_licenses = []
    if pom_files:
        pom = ET.parse(pom_files[0])
        pom_licenses = [{'name':n.findtext('m:name', namespaces=NS), 'url':n.findtext('m:url', namespaces=NS)} for n in pom.findall('.//m:licenses/m:license', NS)]
    base = 'https://dl.google.com/dl/android/maven2/' if group.startswith(('androidx.', 'com.android.tools')) else 'https://repo.maven.apache.org/maven2/'
    stem = group.replace('.', '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version
    evidence = {'coordinate':coord, 'scopes':row['scopes'], 'artifactSha256':sha(data), 'artifactUrl':base+stem+source.suffix, 'pomUrl':base+stem+'.pom', 'pomLicenses':pom_licenses}
    if group=='com.android.tools' and artifact=='desugar_jdk_libs':
        evidence['license']='GPL-2.0-only WITH Classpath-exception-2.0'
        evidence['source']='SOURCE_AVAILABILITY.md'
    elif artifact=='desugar_jdk_libs_configuration':
        evidence['license']='BSD-3-Clause'
    else:
        evidence['license']='Apache-2.0'
    key = group + '.' + artifact + '-' + version
    for index, (name, content) in enumerate(nested_notices(data)):
        path = 'runtime/' + key + '-binary-' + str(index) + '.txt'
        save(LEGAL/path, ('Original archive entry: '+name+'\nArtifact: '+coord+'\n\n').encode()+content)
        evidence.setdefault('notices', []).append(path)
    if group=='com.android.tools':
        return evidence
    url = base+stem+'-sources.jar'
    cached = CACHE/(key+'-sources.jar')
    try:
        if not cached.exists():
            save(cached, get(url))
        headers = set()
        with zipfile.ZipFile(cached) as archive:
            for name in archive.namelist():
                if name.endswith(('.java','.kt')):
                    text = archive.read(name).decode('utf-8', errors='replace')
                    first = re.search(r'/\*.*?\*/', text[:18000], re.S)
                    if first and re.search(r'copyright|licensed|redistribution', first.group(), re.I):
                        headers.add(first.group())
            for name, content in nested_notices(cached.read_bytes()):
                headers.add('Original entry: '+name+'\n'+content.decode('utf-8', errors='replace'))
        if headers:
            path = 'runtime/'+key+'-source-notices.txt'
            save(LEGAL/path, 'Artifact: '+coord+'\nFixed-version sources: '+url+'\nOriginal source notices (duplicate headers coalesced; source files are unmodified upstream):\n\n'+'\n\n'.join(sorted(headers))+'\n')
            evidence.setdefault('notices', []).append(path)
        evidence['sourceNoticeEvidence']={'url':url,'sha256':sha(cached.read_bytes())}
    except Exception as failure:
        evidence['sourceNoticeEvidence']={'url':url,'unavailable':type(failure).__name__}
    return evidence

def main():
    raw=json.loads((ROOT/'validation/compliance-resolved-local.json').read_text())
    unique={}
    for item in raw:
        row=unique.setdefault(item['coordinate'],dict(item,scopes=[]))
        if item['scope'] not in row['scopes']:row['scopes'].append(item['scope'])
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        inventory=[r for r in pool.map(collect,unique.values()) if r]
    inventory.sort(key=lambda r:r['coordinate'])
    # MPL-covered data is separate from OkHttp's Apache-covered executable code.
    for item in inventory:
        if item['coordinate']=='com.squareup.okhttp3:okhttp:4.12.0':
            item['license']='Apache-2.0 AND MPL-2.0 (Public Suffix List data)'
            item['dataSource']='publicsuffix/README.md'
    save(LEGAL/'inventory.json', json.dumps(inventory,ensure_ascii=False,indent=2)+'\n')
    apache=get('https://www.apache.org/licenses/LICENSE-2.0.txt')
    save(ROOT/'LICENSE',apache);save(LEGAL/'Apache-2.0.txt',apache)
    with zipfile.ZipFile(CACHE/'desugar_jdk_libs-2.1.5-source.zip') as archive:
        prefix=archive.namelist()[0]
        assert archive.read(prefix+'VERSION_JDK11.txt').decode().strip().endswith('2.1.5')
        for name in ('LICENSE','ADDITIONAL_LICENSE_INFO','ASSEMBLY_EXCEPTION'):
            save(LEGAL/('desugar/'+name+'.txt'),archive.read(prefix+name))
        # Preserve distinct original copyright/license headers for the distributed library.
        headers=set()
        for name in archive.namelist():
            if name.endswith('.java'):
                text=archive.read(name).decode('utf-8',errors='replace')
                header=re.search(r'/\*.*?\*/',text[:18000],re.S)
                if header and re.search(r'copyright|licensed|redistribution',header.group(),re.I):headers.add(header.group())
        save(LEGAL/'desugar/SOURCE_NOTICES.txt','Unmodified upstream source headers; duplicate headers coalesced.\nCommit: '+PIN+'\n\n'+'\n\n'.join(sorted(headers))+'\n')
    table='| Component / 组件 | Version / 版本 | Scope / 范围 | License / 许可 | Evidence / 原始通知 |\n|---|---|---|---|---|\n'
    for item in inventory:
        g,a,v=item['coordinate'].split(':')
        scope='desugaring' if 'coreLibraryDesugaring' in item['scopes'] else 'runtime'
        links=', '.join('['+path.split('/')[-1]+']('+path+')' for path in item.get('notices',[]))
        if not links:
            links='[desugar source terms](desugar/LICENSE.txt)' if item['license'].startswith('GPL') else '[Apache terms](Apache-2.0.txt); POM/source evidence: inventory.json (empty/compatibility jar where applicable)'
        table+=f'| {g}:{a} | {v} | {scope} | {item["license"]} | {links} |\n'
    save(LEGAL/'README.md','# Open-source licenses / 开源许可\n\nProject-authored code: Apache-2.0. 第三方代码、图片、媒体内容及商标不受项目许可重新授权。\n\nRead Apache-2.0.txt, LICENSE_SCOPE.md, NOTICE.txt and SOURCE_AVAILABILITY.md. Legal originals remain untranslated. 法律正文保持原文；说明不能替代正文。\n\nThe table records resolved artifact inputs, not a claim that every class survives D8. desugar configuration is a build input; listenablefuture is an empty conflict-avoidance jar. Test-only materials are in build-and-test/. SDK/JDK/Gradle distribution/cache/signing keys are not distributed.\n\n'+table+'\n完整机器可读证据：inventory.json。离线页面可选择各文件阅读全文。\n')
    public_suffix_sources()
    print('Collected',len(inventory),'runtime/desugar artifacts; unavailable source jars:',[x['coordinate'] for x in inventory if 'unavailable' in x.get('sourceNoticeEvidence',{})])

def public_suffix_sources():
    """Retain the preferred-form historical data and prove it matches the gzip."""
    cached=CACHE/'okhttp-4.12.0-source.zip'
    if not cached.exists():
        save(cached,get('https://codeload.github.com/square/okhttp/zip/refs/tags/parent-4.12.0'))
    with zipfile.ZipFile(cached) as archive:
        prefix=archive.namelist()[0]+'okhttp/src/'
        data=archive.read(prefix+'test/resources/okhttp3/internal/publicsuffix/public_suffix_list.dat')
        compressed=archive.read(prefix+'main/resources/okhttp3/internal/publicsuffix/publicsuffixes.gz')
        with zipfile.ZipFile(CACHE/'com.squareup.okhttp3.okhttp-4.12.0-sources.jar') as sources:
            assert compressed==sources.read('okhttp3/internal/publicsuffix/publicsuffixes.gz')
        lines=[s for s in data.splitlines() if s.strip() and not s.startswith(b'//')]
        rules=b''.join(s+b'\n' for s in sorted(set(s for s in lines if not s.startswith(b'!'))))
        exceptions=b''.join(s+b'\n' for s in sorted(set(s[1:] for s in lines if s.startswith(b'!'))))
        assert struct.pack('>I',len(rules))+rules+struct.pack('>I',len(exceptions))+exceptions==gzip.decompress(compressed)
        save(LEGAL/'publicsuffix/public_suffix_list.txt',data)
        save(LEGAL/'publicsuffix/PublicSuffixListGenerator.java.txt',archive.read(prefix+'test/java/okhttp3/internal/publicsuffix/PublicSuffixListGenerator.java'))
    save(LEGAL/'publicsuffix/MPL-2.0.txt',get('https://www.mozilla.org/media/MPL/2.0/index.815ca599c9df.txt'))
    # README contains the source URLs and exact snapshot hashes; maintained with release docs.
    assert (LEGAL/'publicsuffix/README.md').is_file()
    with (LEGAL/'README.md').open('a',encoding='utf-8') as stream:
        stream.write('\nOkHttp Public Suffix List data retains MPL-2.0. Original preferred-form historical data, generator and full terms: [publicsuffix/](publicsuffix/README.md).\n')

if __name__=='__main__':main()
