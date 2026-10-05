"""Check that maintenance source distributions include inputs but exclude caches."""
import importlib.util
import pathlib
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('packager', pathlib.Path(__file__).with_name('compliance-package.py'))
packager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packager)


class ExplicitOutputTest(unittest.TestCase):
    """The packager must only ever write the directory named on the command line."""

    def setUp(self):
        self._root = packager.ROOT
        self.directory = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.directory.name)
        packager.ROOT = self.root
        self.addCleanup(self._restore)

    def _restore(self):
        packager.ROOT = self._root
        self.directory.cleanup()

    def _fixture(self):
        licenses = self.root / 'licenses'
        licenses.mkdir(parents=True)
        (licenses / 'legal.txt').write_bytes(b'legal-text')
        for root in ['app/src', 'gradle/wrapper', 'docs/compliance', 'docs/screenshots',
                     'docs/refactor', 'docs/superpowers']:
            item = self.root / root / 'marker.txt'
            item.parent.mkdir(parents=True, exist_ok=True)
            item.write_text('x', encoding='utf-8')
        for name in packager.public_files():
            item = self.root / name
            item.parent.mkdir(parents=True, exist_ok=True)
            if not item.is_file():
                item.write_bytes(b'x')
        apk = self.root / 'app/build/outputs/apk/debug/app-debug.apk'
        apk.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('assets/licenses/legal.txt', b'legal-text')
            archive.writestr('classes.dex', b'j$/desugared')
        desugar = self.root / 'validation/compliance-sources/desugar_jdk_libs-2.1.5-source.zip'
        desugar.parent.mkdir(parents=True, exist_ok=True)
        desugar.write_bytes(b'desugar-source')
        legacy = self.root / 'output' / packager.LEGACY_DIRECTORY
        legacy.mkdir(parents=True)
        (legacy / 'marker.txt').write_text('historical', encoding='utf-8')
        return legacy

    def test_explicit_destination_leaves_historical_directory_untouched(self):
        legacy = self._fixture()
        out = self.root / 'output' / 'xbvr-android-0.2.7-refactor-candidate'
        prefix = 'XBVR-Pocket-0.2.7-refactor-candidate'
        self.assertEqual(0, packager.main(['--output-dir', str(out), '--name-prefix', prefix]))
        for name in [prefix + '.apk', prefix + '-source.zip',
                     prefix + '-third-party-license-materials.zip', 'SHA256SUMS.txt', 'public-files.json']:
            self.assertTrue((out / name).is_file(), name)
        # The historical 0.2.5 directory keeps exactly its own marker.
        self.assertEqual(['marker.txt'], sorted(p.name for p in legacy.iterdir()))
        self.assertEqual('historical', (legacy / 'marker.txt').read_text(encoding='utf-8'))
        written = sorted(p.relative_to(self.root).as_posix() for p in self.root.rglob('*') if p.is_file())
        self.assertFalse([n for n in written if n.startswith('output/' + packager.LEGACY_DIRECTORY + '/') and n != 'output/' + packager.LEGACY_DIRECTORY + '/marker.txt'])
        self.assertFalse([n for n in written if 'XBVR-Pocket-0.2.5' in n])

    def test_historical_prefix_and_directory_are_refused(self):
        with self.assertRaises(SystemExit):
            packager.main(['--output-dir', str(self.root / 'safe'), '--name-prefix', packager.LEGACY_PREFIX])
        with self.assertRaises(SystemExit):
            packager.main(['--output-dir', str(self.root / 'output' / packager.LEGACY_DIRECTORY),
                           '--name-prefix', 'XBVR-Pocket-0.2.7-refactor-candidate'])

    def test_destination_is_mandatory(self):
        with self.assertRaises(SystemExit):
            packager.main([])
        self.assertFalse(hasattr(packager, 'OUT'))


class PublicFilesTest(unittest.TestCase):
    def test_maintenance_materials_survive_without_private_caches(self):
        previous = packager.ROOT
        with tempfile.TemporaryDirectory() as directory:
            packager.ROOT = pathlib.Path(directory)
            try:
                for name in ['tools/compliance-input-lock.json', 'tools/test_compliance_collect.py',
                             'tools/licenses-device-state.cjs', 'tools/licenses-device-state.test.cjs',
                             'tools/compliance-init.gradle', '.github/workflows/ci.yml',
                             'tools/__pycache__/test_input.pyc', 'tools/fixture.cjs',
                             'tools/local-server.private.cjs', 'tools/download.cjs',
                             '.github/workflows/local-only.yml', 'validation/compliance-resolved-local.json',
                             'toolchain/debug.keystore']:
                    item = packager.ROOT / name
                    item.parent.mkdir(parents=True, exist_ok=True)
                    item.write_text('test', encoding='utf-8')
                names = packager.public_files()
                for name in ['PRIVACY.md', 'SECURITY.md', 'tools/compliance-input-lock.json',
                             'tools/test_compliance_collect.py', 'tools/licenses-device-state.cjs',
                             'tools/licenses-device-state.test.cjs', '.github/workflows/ci.yml']:
                    self.assertIn(name, names)
                self.assertFalse(any('toolchain/' in n or 'validation/' in n or '__pycache__' in n for n in names))
                for name in ['tools/fixture.cjs', 'tools/local-server.private.cjs',
                             'tools/download.cjs', '.github/workflows/local-only.yml']:
                    self.assertNotIn(name, names)
            finally:
                packager.ROOT = previous


if __name__ == '__main__':
    unittest.main()
