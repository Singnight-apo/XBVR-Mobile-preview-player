"""Check that maintenance source distributions include inputs but exclude caches."""
import importlib.util
import pathlib
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('packager', pathlib.Path(__file__).with_name('compliance-package.py'))
packager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packager)


class PublicFilesTest(unittest.TestCase):
    def test_maintenance_materials_survive_without_private_caches(self):
        previous = packager.ROOT
        with tempfile.TemporaryDirectory() as directory:
            packager.ROOT = pathlib.Path(directory)
            try:
                for name in ['tools/compliance-input-lock.json', 'tools/test_input.py',
                             'tools/licenses-device-state.cjs', 'tools/licenses-device-state.test.cjs',
                             'tools/compliance-init.gradle', '.github/workflows/ci.yml',
                             'tools/__pycache__/test_input.pyc', 'validation/compliance-resolved-local.json',
                             'toolchain/debug.keystore']:
                    item = packager.ROOT / name
                    item.parent.mkdir(parents=True, exist_ok=True)
                    item.write_text('test', encoding='utf-8')
                names = packager.public_files()
                for name in ['PRIVACY.md', 'SECURITY.md', 'tools/compliance-input-lock.json',
                             'tools/test_input.py', 'tools/licenses-device-state.cjs',
                             'tools/licenses-device-state.test.cjs', '.github/workflows/ci.yml']:
                    self.assertIn(name, names)
                self.assertFalse(any('toolchain/' in n or 'validation/' in n or '__pycache__' in n for n in names))
            finally:
                packager.ROOT = previous


if __name__ == '__main__':
    unittest.main()
