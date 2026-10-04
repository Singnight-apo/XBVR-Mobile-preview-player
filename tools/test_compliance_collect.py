import copy
import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location('collector', pathlib.Path(__file__).with_name('compliance-collect.py'))
collector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(collector)

class InputGateTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = pathlib.Path(self.temporary.name)
        self.cache = self.root / 'cache'
        self.cache.mkdir()
        self.binary = self.root / 'component.jar'
        self.binary.write_bytes(b'reviewed binary')
        self.source_name = 'example.component-1-sources.jar'
        (self.cache / self.source_name).write_bytes(b'reviewed source')
        self.raw = [{'coordinate':'example:component:1', 'scope':'debugRuntimeClasspath', 'file':str(self.binary)}]
        self.lock = {'schemaVersion':1, 'artifactCount':1, 'artifacts': [{
            'coordinate':'example:component:1', 'scopes':['debugRuntimeClasspath'],
            'artifactSha256':collector.sha(b'reviewed binary'), 'license':'BSD-3-Clause',
            'artifactUrl':'https://example.invalid/component.jar',
            'pomUrl':'https://example.invalid/component.pom', 'pomLicenses':[],
            'sourceNoticeEvidence': {'url':'https://example.invalid/component-sources.jar',
                                    'sha256':collector.sha(b'reviewed source')}}], 'resources':{}}
        for name in ('Apache-2.0.txt','MPL-2.0.txt','desugar_jdk_libs-2.1.5-source.zip','okhttp-4.12.0-source.zip'):
            data = ('reviewed ' + name).encode()
            (self.cache / name).write_bytes(data)
            self.lock['resources'][name] = {'url':'https://example.invalid/' + name, 'sha256':collector.sha(data)}

    def offline(self, url):
        raise FileNotFoundError('offline missing source: ' + url)

    def prepare(self):
        return collector.prepare_inputs(self.raw, self.lock, self.cache, self.offline)

    def test_valid_inputs_keep_reviewed_license_and_lock(self):
        before = copy.deepcopy(self.lock)
        rows, resources = self.prepare()
        self.assertEqual(rows[0]['evidence']['license'], 'BSD-3-Clause')
        self.assertEqual(rows[0]['artifactData'], b'reviewed binary')
        self.assertEqual(rows[0]['sourcesData'], b'reviewed source')
        self.assertEqual(len(resources), 4)
        self.assertEqual(self.lock, before)

    def test_tampered_binary_fails(self):
        self.binary.write_bytes(b'tampered')
        with self.assertRaisesRegex(collector.InputError, 'SHA-256 mismatch'):
            self.prepare()

    def test_missing_binary_fails(self):
        self.binary.unlink()
        with self.assertRaises(FileNotFoundError):
            self.prepare()

    def test_duplicate_coordinate_alternative_file_is_also_checked(self):
        other = self.root / 'other.jar'
        other.write_bytes(b'tampered')
        self.raw.append(dict(self.raw[0], file=str(other)))
        with self.assertRaisesRegex(collector.InputError, 'SHA-256 mismatch'):
            self.prepare()

    def test_unknown_artifact_fails(self):
        self.raw.append(dict(self.raw[0], coordinate='example:unreviewed:2'))
        with self.assertRaisesRegex(collector.InputError, 'unknown=.*example:unreviewed:2'):
            self.prepare()

    def test_missing_resolved_artifact_fails(self):
        self.raw.clear()
        with self.assertRaisesRegex(collector.InputError, 'missing=.*example:component:1'):
            self.prepare()

    def test_missing_license_fails_without_default_apache(self):
        del self.lock['artifacts'][0]['license']
        with self.assertRaisesRegex(collector.InputError, 'Missing reviewed license'):
            self.prepare()

    def test_missing_source_hash_cannot_silently_skip_a_runtime_source(self):
        del self.lock['artifacts'][0]['sourceNoticeEvidence']
        with self.assertRaisesRegex(collector.InputError, 'Missing reviewed source'):
            self.prepare()

    def test_unavailable_source_exception_cannot_be_applied_to_other_artifacts(self):
        self.lock['artifacts'][0]['sourceNoticeEvidence'] = {
            'url':'https://example.invalid/component-sources.jar', 'unavailable':'HTTPError'}
        with self.assertRaisesRegex(collector.InputError, 'Unreviewed source exception'):
            self.prepare()

    def test_changed_scope_fails(self):
        self.raw[0]['scope'] = 'coreLibraryDesugaring'
        with self.assertRaisesRegex(collector.InputError, 'Scopes differ'):
            self.prepare()

    def test_tampered_source_fails_instead_of_recording_unavailable(self):
        (self.cache / self.source_name).write_bytes(b'tampered')
        with self.assertRaisesRegex(collector.InputError, 'SHA-256 mismatch'):
            self.prepare()

    def test_missing_source_offline_fails(self):
        (self.cache / self.source_name).unlink()
        with self.assertRaises(FileNotFoundError):
            self.prepare()

    def test_missing_cache_can_download_only_verified_bytes(self):
        (self.cache / self.source_name).unlink()
        rows, _ = collector.prepare_inputs(self.raw, self.lock, self.cache, lambda url: b'reviewed source')
        self.assertEqual(rows[0]['sourcesData'], b'reviewed source')
        self.assertEqual((self.cache / self.source_name).read_bytes(), b'reviewed source')

    def test_bad_download_is_not_saved(self):
        (self.cache / self.source_name).unlink()
        with self.assertRaisesRegex(collector.InputError, 'SHA-256 mismatch'):
            collector.prepare_inputs(self.raw, self.lock, self.cache, lambda url: b'tampered download')
        self.assertFalse((self.cache / self.source_name).exists())

    def test_each_extra_resource_is_hash_checked(self):
        for name in self.lock['resources']:
            path = self.cache / name
            original = path.read_bytes()
            with self.subTest(resource=name):
                path.write_bytes(b'tampered')
                with self.assertRaisesRegex(collector.InputError, 'SHA-256 mismatch'):
                    self.prepare()
                path.write_bytes(original)

    def test_main_failure_does_not_touch_legal_files_or_lock(self):
        for relative, data in [('licenses/inventory.json', b'published inventory'),
                               ('licenses/runtime/notice.txt', b'published notice'), ('LICENSE', b'published terms')]:
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        (self.root / 'validation').mkdir()
        (self.root / 'validation/compliance-resolved-local.json').write_text(json.dumps(self.raw))
        lock_path = self.root / 'input-lock.json'
        lock_path.write_text(json.dumps(self.lock))
        before = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.binary.write_bytes(b'tampered')
        before[self.binary.relative_to(self.root)] = b'tampered'
        with mock.patch.multiple(collector, ROOT=self.root, LEGAL=self.root/'licenses',
                                 LOCK=lock_path, CACHE=self.cache):
            with self.assertRaisesRegex(collector.InputError, 'SHA-256 mismatch'):
                collector.main()
        after = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before, after)

    def test_generation_failure_is_staged_and_never_published(self):
        (self.root / 'validation').mkdir()
        (self.root / 'validation/compliance-resolved-local.json').write_text(json.dumps(self.raw))
        lock_path = self.root / 'input-lock.json'
        lock_path.write_text(json.dumps(self.lock))
        (self.root / 'LICENSE').write_bytes(b'published terms')
        before = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        with mock.patch.multiple(collector, ROOT=self.root, LEGAL=self.root/'licenses',
                                 LOCK=lock_path, CACHE=self.cache):
            # Hashes pass, but the deliberately invalid binary cannot be parsed as ZIP.
            with self.assertRaises(collector.zipfile.BadZipFile):
                collector.main()
        after = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before, after)

    def test_publish_write_failure_restores_previous_files(self):
        stage = self.root / 'stage'
        target = self.root / 'target'
        stage.mkdir()
        target.mkdir()
        for name in ('a.txt', 'b.txt'):
            (stage/name).write_bytes(b'new')
            (target/name).write_bytes(b'old')
        replace = collector.os.replace
        def fail_second(source, destination):
            if pathlib.Path(destination).name == 'b.txt':
                raise PermissionError('simulated occupied destination')
            return replace(source, destination)
        write_bytes = pathlib.Path.write_bytes
        def fail_if_rewriting_occupied_file(path, data):
            if path == target/'b.txt':
                raise PermissionError('destination remains occupied during rollback')
            return write_bytes(path, data)
        with mock.patch.object(collector.os, 'replace', side_effect=fail_second), \
             mock.patch.object(pathlib.Path, 'write_bytes', autospec=True,
                               side_effect=fail_if_rewriting_occupied_file):
            with self.assertRaises(PermissionError):
                collector.publish(stage, target)
        self.assertEqual((target/'a.txt').read_bytes(), b'old')
        self.assertEqual((target/'b.txt').read_bytes(), b'old')

    def test_rollback_failure_still_attempts_remaining_restores(self):
        stage, target = self.root/'stage', self.root/'target'
        stage.mkdir(); target.mkdir()
        for name in ('a.txt', 'b.txt', 'c.txt'):
            (stage/name).write_bytes(b'new')
            (target/name).write_bytes(b'old')
        replace, write_bytes = collector.os.replace, pathlib.Path.write_bytes
        original = PermissionError('publication failed at c')
        def fail_third(source, destination):
            if pathlib.Path(destination).name == 'c.txt':
                raise original
            return replace(source, destination)
        def fail_one_rollback(path, data):
            if path == target/'b.txt':
                raise PermissionError('rollback failed at b')
            return write_bytes(path, data)
        with mock.patch.object(collector.os, 'replace', side_effect=fail_third), \
             mock.patch.object(pathlib.Path, 'write_bytes', autospec=True,
                               side_effect=fail_one_rollback):
            with self.assertRaisesRegex(RuntimeError, 'rollback incomplete') as caught:
                collector.publish(stage, target)
        self.assertIs(caught.exception.__cause__, original)
        self.assertEqual((target/'a.txt').read_bytes(), b'old')
        self.assertEqual((target/'b.txt').read_bytes(), b'new')
        self.assertEqual((target/'c.txt').read_bytes(), b'old')

if __name__ == '__main__':
    unittest.main()
