"""Curated public release: excludes toolchains, references, credentials and local logs.

The output directory and the artefact name prefix are explicit command-line
parameters. Nothing is written unless the caller names the destination, so an
accidental run can never overwrite an older, already published release folder.
"""
import argparse
import hashlib
import json
import pathlib
import shutil
import zipfile

ROOT=pathlib.Path(__file__).resolve().parents[1]
DEFAULT_APK='app/build/outputs/apk/debug/app-debug.apk'
DEFAULT_DESUGAR='validation/compliance-sources/desugar_jdk_libs-2.1.5-source.zip'
LEGACY_PREFIX='XBVR-Pocket-0.2.5'
LEGACY_DIRECTORY='xbvr-android-0.2.5'
def digest(data):return hashlib.sha256(data).hexdigest()
def public_files():
    roots=['app/src','gradle/wrapper','licenses','docs/compliance','docs/screenshots',
           'docs/refactor','docs/superpowers']
    paths=['.gitignore','LICENSE','NOTICE','README.md','README.en.md','PRIVACY.md','SECURITY.md','THIRD_PARTY_NOTICES.md','VALIDATION.md','settings.gradle','build.gradle','gradle.properties','build.ps1','gradlew','gradlew.bat','app/build.gradle']
    # Explicitly reviewed maintenance files only: local tools may contain fixtures,
    # private inputs or one-off helpers that must never enter a source release.
    paths += ['.github/workflows/ci.yml', 'tools/compliance-collect.py',
              'tools/compliance-init.gradle', 'tools/compliance-input-lock.json',
              'tools/compliance-package.py', 'tools/licenses-device-state.cjs',
              'tools/licenses-device-state.test.cjs', 'tools/licenses-qa.cjs',
              'tools/qa-adb.test.cjs', 'tools/qa.cjs',
              'tools/test_compliance_collect.py', 'tools/test_compliance_package.py']
    for root in roots:
        paths.extend(p.relative_to(ROOT).as_posix() for p in (ROOT/root).rglob('*') if p.is_file())
    paths=sorted(set(paths))
    for name in paths:
        assert not any(term in name.lower() for term in ('private','handoff','keystore','local.properties','toolchain/','references/')),name
    return paths
def resolve(argument):
    path=pathlib.Path(argument)
    return path if path.is_absolute() else ROOT/path
def main(argv=None):
    parser=argparse.ArgumentParser(description='Build the curated public source/licence release in an explicit directory.')
    parser.add_argument('--output-dir',required=True,help='exact directory that receives the artefacts (created when missing)')
    parser.add_argument('--name-prefix',required=True,help="artefact name prefix, e.g. 'XBVR-Pocket-0.2.7-refactor-candidate'")
    parser.add_argument('--apk',default=DEFAULT_APK,help='signed APK to copy (default: %(default)s)')
    parser.add_argument('--desugar-source',default=DEFAULT_DESUGAR,help='desugar source zip to ship (default: %(default)s)')
    args=parser.parse_args(argv)
    out=pathlib.Path(args.output_dir).expanduser().resolve()
    if args.name_prefix==LEGACY_PREFIX or out.name==LEGACY_DIRECTORY:
        raise SystemExit('refusing to write the historical 0.2.5 release directory or prefix; pass an explicit new destination')
    apk=resolve(args.apk)
    desugar=resolve(args.desugar_source)
    if not apk.is_file():raise SystemExit('missing APK: '+str(apk))
    if not desugar.is_file():raise SystemExit('missing desugar source zip: '+str(desugar))
    out.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(apk) as archive:
        for f in (ROOT/'licenses').rglob('*'):
            if f.is_file():assert archive.read('assets/licenses/'+f.relative_to(ROOT/'licenses').as_posix())==f.read_bytes(),str(f)
        assert b'j$/' in b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex')),'Expected desugared runtime absent'
    apk_name=args.name_prefix+'.apk'
    source_name=args.name_prefix+'-source.zip'
    materials_name=args.name_prefix+'-third-party-license-materials.zip'
    shutil.copyfile(apk,out/apk_name)
    shutil.copyfile(desugar,out/'desugar_jdk_libs-2.1.5-source.zip')
    paths=public_files()
    with zipfile.ZipFile(out/source_name,'w',zipfile.ZIP_DEFLATED) as archive:
        for name in paths:archive.write(ROOT/name,'xbvr-android/'+name)
    with zipfile.ZipFile(out/materials_name,'w',zipfile.ZIP_DEFLATED) as archive:
        for name in paths:
            if name.startswith(('licenses/','docs/compliance/')) or name in ('LICENSE','NOTICE','THIRD_PARTY_NOTICES.md'):
                archive.write(ROOT/name,name)
    for name in ('README.md','README.en.md','THIRD_PARTY_NOTICES.md','VALIDATION.md'):
        shutil.copyfile(ROOT/name,out/name)
    for name in (source_name,materials_name):
        with zipfile.ZipFile(out/name) as archive:assert archive.testzip() is None
    for f in sorted(out.iterdir()):
        if f.is_file() and f.name not in ('SHA256SUMS.txt','public-files.json'):
            print(digest(f.read_bytes())+'  '+f.name)
    sums='\n'.join(digest(f.read_bytes())+'  '+f.name for f in sorted(out.iterdir()) if f.is_file() and f.name not in ('SHA256SUMS.txt','public-files.json'))+'\n'
    (out/'SHA256SUMS.txt').write_text(sums,encoding='utf-8')
    (out/'public-files.json').write_text(json.dumps(paths,indent=2)+'\n',encoding='utf-8')
    print('Verified',len(paths),'curated public files; all license assets match. Output:',out)
    return 0
if __name__=='__main__':raise SystemExit(main())
