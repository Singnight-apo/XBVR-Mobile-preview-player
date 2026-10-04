"""Curated public release: excludes toolchains, references, credentials and local logs."""
import hashlib
import json
import pathlib
import shutil
import zipfile

ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT.parent/'output/xbvr-android-0.2.5'
def digest(data):return hashlib.sha256(data).hexdigest()
def public_files():
    roots=['app/src','gradle/wrapper','licenses','docs/compliance','docs/screenshots']
    paths=['.gitignore','LICENSE','NOTICE','README.md','README.en.md','THIRD_PARTY_NOTICES.md','VALIDATION.md','settings.gradle','build.gradle','gradle.properties','build.ps1','gradlew','gradlew.bat','app/build.gradle','tools/compliance-collect.py','tools/compliance-init.gradle','tools/compliance-package.py','tools/licenses-qa.cjs','tools/qa.cjs']
    for root in roots:
        paths.extend(p.relative_to(ROOT).as_posix() for p in (ROOT/root).rglob('*') if p.is_file())
    paths=sorted(set(paths))
    for name in paths:
        assert not any(term in name.lower() for term in ('private','handoff','keystore','local.properties','toolchain/','references/')),name
    return paths
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    with zipfile.ZipFile(apk) as archive:
        for f in (ROOT/'licenses').rglob('*'):
            if f.is_file():assert archive.read('assets/licenses/'+f.relative_to(ROOT/'licenses').as_posix())==f.read_bytes(),str(f)
        assert b'j$/' in b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex')),'Expected desugared runtime absent'
    shutil.copyfile(apk,OUT/'XBVR-Pocket-0.2.5.apk')
    shutil.copyfile(ROOT/'validation/compliance-sources/desugar_jdk_libs-2.1.5-source.zip',OUT/'desugar_jdk_libs-2.1.5-source.zip')
    paths=public_files()
    with zipfile.ZipFile(OUT/'XBVR-Pocket-0.2.5-source.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for name in paths:archive.write(ROOT/name,'xbvr-android/'+name)
    with zipfile.ZipFile(OUT/'third-party-license-materials-0.2.5.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for name in paths:
            if name.startswith(('licenses/','docs/compliance/')) or name in ('LICENSE','NOTICE','THIRD_PARTY_NOTICES.md'):
                archive.write(ROOT/name,name)
    for name in ('README.md','README.en.md','THIRD_PARTY_NOTICES.md','VALIDATION.md'):
        shutil.copyfile(ROOT/name,OUT/name)
    for name in ('XBVR-Pocket-0.2.5-source.zip','third-party-license-materials-0.2.5.zip'):
        with zipfile.ZipFile(OUT/name) as archive:assert archive.testzip() is None
    for f in sorted(OUT.iterdir()):
        if f.is_file() and f.name not in ('SHA256SUMS.txt','public-files.json'):
            print(digest(f.read_bytes())+'  '+f.name)
    sums='\n'.join(digest(f.read_bytes())+'  '+f.name for f in sorted(OUT.iterdir()) if f.is_file() and f.name not in ('SHA256SUMS.txt','public-files.json'))+'\n'
    (OUT/'SHA256SUMS.txt').write_text(sums,encoding='utf-8')
    (OUT/'public-files.json').write_text(json.dumps(paths,indent=2)+'\n',encoding='utf-8')
    print('Verified',len(paths),'curated public files; all license assets match.')
if __name__=='__main__':main()
