"""Collect legal evidence from resolved artifacts; inputs stay local, outputs omit paths.

Run the compliance Gradle init task first, then this script using Python 3.
No SDK, signing key, private references or server data are copied.
"""
import hashlib
import gzip
import io
import json
import pathlib
import re
import struct
import urllib.request
import os
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
LEGAL = ROOT / 'licenses'
CACHE = ROOT / 'validation/compliance-sources'
LOCK = ROOT / 'tools/compliance-input-lock.json'
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

class InputError(ValueError):
    """An input differs from the reviewed release baseline."""


def verified(data, expected, label):
    if sha(data) != expected:
        raise InputError('SHA-256 mismatch: ' + label)
    return data


def locked_resource(cache, name, record, download):
    path = cache / name
    if path.is_file():
        return verified(path.read_bytes(), record['sha256'], name)
    # Verify downloaded bytes before allowing them into the private cache.
    data = verified(download(record['url']), record['sha256'], name)
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as stream:
        temporary = pathlib.Path(stream.name)
        stream.write(data)
    try:
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)
    return data


def prepare_inputs(raw, lock, cache, download=get):
    """Validate the whole set; never write legal outputs or modify the lock."""
    if lock.get('schemaVersion') != 1:
        raise InputError('Unsupported input lock schema')
    expected = {r['coordinate']: r for r in lock['artifacts']}
    if len(expected) != len(lock['artifacts']) or len(expected) != lock['artifactCount']:
        raise InputError('Duplicate or incomplete input lock')
    unique = {}
    for item in raw:
        if item['coordinate'] == 'XBVR Pocket:app:unspecified':
            continue  # Local project output is not a third-party dependency.
        if item['scope'] not in ('debugRuntimeClasspath', 'coreLibraryDesugaring',
                                 'debugUnitTestRuntimeClasspath'):
            raise InputError('Unknown dependency scope: ' + item['scope'])
        row = unique.setdefault(item['coordinate'], dict(item, scopes=[], files=[]))
        if item['scope'] not in row['scopes']:
            row['scopes'].append(item['scope'])
        if item['file'] not in row['files']:
            row['files'].append(item['file'])
    rows = {c:r for c,r in unique.items()
            if any(s != 'debugUnitTestRuntimeClasspath' for s in r['scopes'])}
    if set(rows) != set(expected):
        raise InputError('Artifact set differs from reviewed lock; unknown=' +
                         repr(sorted(set(rows) - set(expected))) + '; missing=' +
                         repr(sorted(set(expected) - set(rows))))
    prepared = []
    for coord in sorted(rows):
        row, record = rows[coord], expected[coord]
        if set(row['scopes']) != set(record['scopes']):
            raise InputError('Scopes differ from lock: ' + coord)
        if not record.get('license'):
            raise InputError('Missing reviewed license: ' + coord)
        # Validate every resolved occurrence, including alternative cache paths.
        binaries = [verified(pathlib.Path(f).read_bytes(), record['artifactSha256'], coord)
                    for f in row['files']]
        source = record.get('sourceNoticeEvidence', {})
        sources = None
        if 'sha256' in source:
            g, a, v = coord.split(':')
            sources = locked_resource(cache, g + '.' + a + '-' + v + '-sources.jar',
                                      source, download)
        elif source:
            # Sole pre-reviewed gap: the published empty conflict-avoidance JAR.
            if (coord != 'com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava'
                    or source != {'url': record['pomUrl'][:-4] + '-sources.jar',
                                  'unavailable': 'HTTPError'}):
                raise InputError('Unreviewed source exception: ' + coord)
        elif coord not in ('com.android.tools:desugar_jdk_libs:2.1.5',
                           'com.android.tools:desugar_jdk_libs_configuration:2.1.5'):
            raise InputError('Missing reviewed source hash: ' + coord)
        prepared.append(dict(row, evidence=dict(record), artifactData=binaries[0],
                             sourcesData=sources))
    required = {'Apache-2.0.txt', 'MPL-2.0.txt', 'desugar_jdk_libs-2.1.5-source.zip',
                'okhttp-4.12.0-source.zip'}
    if set(lock['resources']) != required:
        raise InputError('Missing or unknown legal resource lock')
    resources = {name: locked_resource(cache, name, record, download)
                 for name, record in lock['resources'].items()}
    return prepared, resources


def collect(row):
    coord = row['coordinate']
    group, artifact, version = coord.split(':')
    data = row['artifactData']
    evidence = dict(row['evidence'])
    key = group + '.' + artifact + '-' + version
    for index, (name, content) in enumerate(nested_notices(data)):
        path = 'runtime/' + key + '-binary-' + str(index) + '.txt'
        save(LEGAL/path, ('Original archive entry: '+name+'\nArtifact: '+coord+'\n\n').encode()+content)
        evidence.setdefault('notices', []).append(path)
    if group=='com.android.tools':
        return evidence
    sources = row['sourcesData']
    if sources is not None:
        url = evidence['sourceNoticeEvidence']['url']
        headers = set()
        with zipfile.ZipFile(io.BytesIO(sources)) as archive:
            for name in archive.namelist():
                if name.endswith(('.java', '.kt')):
                    text = archive.read(name).decode('utf-8', errors='replace')
                    first = re.search(r'/\*.*?\*/', text[:18000], re.S)
                    if first and re.search(r'copyright|licensed|redistribution', first.group(), re.I):
                        headers.add(first.group())
            for name, content in nested_notices(sources):
                headers.add('Original entry: '+name+'\n'+content.decode('utf-8', errors='replace'))
        if headers:
            path = 'runtime/'+key+'-source-notices.txt'
            save(LEGAL/path, 'Artifact: '+coord+'\nFixed-version sources: '+url+'\nOriginal source notices (duplicate headers coalesced; source files are unmodified upstream):\n\n'+'\n\n'.join(sorted(headers))+'\n')
            evidence.setdefault('notices', []).append(path)
    return evidence


def publish(stage, root):
    """Publish generated files after successful staging, rolling back write errors."""
    paths = sorted(p for p in stage.rglob('*') if p.is_file())
    backups = {p.relative_to(stage): (root / p.relative_to(stage)).read_bytes()
               if (root / p.relative_to(stage)).is_file() else None for p in paths}
    written = []
    try:
        for path in paths:
            target = root / path.relative_to(stage)
            target.parent.mkdir(parents=True, exist_ok=True)
            # Each individual destination is replaced atomically.
            with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as stream:
                temporary = pathlib.Path(stream.name)
                stream.write(path.read_bytes())
            try:
                os.replace(temporary, target)
                written.append(path.relative_to(stage))
            finally:
                temporary.unlink(missing_ok=True)
    except BaseException as failure:
        rollback_errors = []
        for relative in reversed(written):
            target = root / relative
            try:
                if backups[relative] is None:
                    target.unlink(missing_ok=True)
                else:
                    target.write_bytes(backups[relative])
            except Exception as rollback_failure:
                rollback_errors.append(str(relative) + ': ' + str(rollback_failure))
        if rollback_errors:
            raise RuntimeError('Publication failed; rollback incomplete: ' +
                               '; '.join(rollback_errors)) from failure
        raise


def main():
    global LEGAL
    raw = json.loads((ROOT/'validation/compliance-resolved-local.json').read_text(encoding='utf-8'))
    lock = json.loads(LOCK.read_text(encoding='utf-8'))
    rows, resources = prepare_inputs(raw, lock, CACHE)
    previous_legal = LEGAL
    # No generated legal file is published until every archive has been parsed.
    with tempfile.TemporaryDirectory(prefix='compliance-stage-') as temporary:
        stage = pathlib.Path(temporary)
        LEGAL = stage / 'licenses'
        try:
            generate(rows, resources, stage)
            publish(stage, ROOT)
            print('Collected', len(rows), 'reviewed runtime/desugar artifacts; known source gap: listenablefuture empty JAR')
        finally:
            LEGAL = previous_legal


def generate(rows, resources, stage):
    inventory = [collect(row) for row in rows]

    inventory.sort(key=lambda r:r['coordinate'])
    # License classifications come only from the reviewed input lock.
    save(LEGAL/'inventory.json', json.dumps(inventory,ensure_ascii=False,indent=2)+'\n')
    apache=resources['Apache-2.0.txt']
    save(stage/'LICENSE',apache);save(LEGAL/'Apache-2.0.txt',apache)
    with zipfile.ZipFile(io.BytesIO(resources['desugar_jdk_libs-2.1.5-source.zip'])) as archive:
        prefix=archive.namelist()[0]
        if not archive.read(prefix+'VERSION_JDK11.txt').decode().strip().endswith('2.1.5'):
            raise InputError('Unexpected desugar source version')
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
    public_suffix_sources(rows, resources)

def public_suffix_sources(rows, resources):
    """Retain the preferred-form historical data and prove it matches the gzip."""
    with zipfile.ZipFile(io.BytesIO(resources['okhttp-4.12.0-source.zip'])) as archive:
        prefix=archive.namelist()[0]+'okhttp/src/'
        data=archive.read(prefix+'test/resources/okhttp3/internal/publicsuffix/public_suffix_list.dat')
        compressed=archive.read(prefix+'main/resources/okhttp3/internal/publicsuffix/publicsuffixes.gz')
        okhttp = next(r for r in rows if r['coordinate']=='com.squareup.okhttp3:okhttp:4.12.0')
        with zipfile.ZipFile(io.BytesIO(okhttp['sourcesData'])) as sources:
            if compressed != sources.read('okhttp3/internal/publicsuffix/publicsuffixes.gz'):
                raise InputError('OkHttp source gzip differs from source jar')
        lines=[s for s in data.splitlines() if s.strip() and not s.startswith(b'//')]
        rules=b''.join(s+b'\n' for s in sorted(set(s for s in lines if not s.startswith(b'!'))))
        exceptions=b''.join(s+b'\n' for s in sorted(set(s[1:] for s in lines if s.startswith(b'!'))))
        if struct.pack('>I',len(rules))+rules+struct.pack('>I',len(exceptions))+exceptions != gzip.decompress(compressed):
            raise InputError('Public Suffix List does not reproduce gzip payload')
        save(LEGAL/'publicsuffix/public_suffix_list.txt',data)
        save(LEGAL/'publicsuffix/PublicSuffixListGenerator.java.txt',archive.read(prefix+'test/java/okhttp3/internal/publicsuffix/PublicSuffixListGenerator.java'))
    save(LEGAL/'publicsuffix/MPL-2.0.txt',resources['MPL-2.0.txt'])
    # README contains the source URLs and exact snapshot hashes; maintained with release docs.
    if not (ROOT/'licenses/publicsuffix/README.md').is_file():
        raise InputError('Missing maintained Public Suffix List README')
    with (LEGAL/'README.md').open('a',encoding='utf-8') as stream:
        stream.write('\nOkHttp Public Suffix List data retains MPL-2.0. Original preferred-form historical data, generator and full terms: [publicsuffix/](publicsuffix/README.md).\n')

if __name__=='__main__':main()
