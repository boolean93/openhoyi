import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from check_release_configuration import fixture_failure_summary, redact_fixture_output, prefetch_release_dependencies, PREFETCH_INIT

class FixtureDiagnosticsTest(unittest.TestCase):
    def test_prefetch_is_online_unsigned_artifact_resolution(self):
        environment = {"PATH": "/bin", "HOYI_RELEASE_STORE_PASSWORD": "secret", "HOYI_FIXTURE_STORE_PASSWORD": "fixture"}
        with patch('check_release_configuration.subprocess.run', return_value=SimpleNamespace(returncode=0)) as run:
            prefetch_release_dependencies(Path('/tmp/isolated.gradle'), environment, Path('/sdk/build-tools/35.0.0'))
        command = run.call_args.args[0]
        self.assertIn(':prefetchReleaseFixtureDependencies', command)
        self.assertNotIn('--offline', command)
        self.assertEqual({"PATH": "/bin"}, run.call_args.kwargs['env'])
        self.assertFalse(any('assemble' in part or 'signing' in part for part in command))
        self.assertIn("['releaseCompileClasspath', 'releaseRuntimeClasspath']", PREFETCH_INIT)
        self.assertIn('ModuleComponentIdentifier', PREFETCH_INIT)
        self.assertIn('.files.files.each', PREFETCH_INIT)
        self.assertNotIn('dependsOn', PREFETCH_INIT)
        self.assertNotIn('repositories', PREFETCH_INIT)

    def test_prefetch_failure_cannot_skip_offline_fixture_gate(self):
        with patch('check_release_configuration.subprocess.run', return_value=SimpleNamespace(returncode=1, stdout='', stderr='secret')):
            with self.assertRaisesRegex(RuntimeError, 'Release dependency prefetch failed; exit=1'):
                prefetch_release_dependencies(Path('/tmp/isolated.gradle'), {}, Path('/sdk'))

    def test_all_fixture_signing_values_are_redacted(self):
        values = {"HOYI_RELEASE_" + key: value for key, value in zip(
            ("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD", "CERT_SHA256"),
            ("/private/fixture.jks", "secret-store-password", "fixture-alias", "secret-key-password", "a" * 64))}
        result = redact_fixture_output(" | ".join(values.values()), values)
        self.assertEqual(" | ".join(["<redacted-fixture-signing-value>"] * 5), result)

    def test_offline_failure_is_visible_without_error_content(self):
        output = "> Task :mobile:compileReleaseKotlin FAILED\nCould not resolve all files\nNo cached version available for offline mode\nsecret-password /private/key.jks https://host/?token=private-token"
        self.assertEqual("fixtureFailure categories=OFFLINE_DEPENDENCY,DEPENDENCY_RESOLUTION tasks=:mobile:compileReleaseKotlin", fixture_failure_summary(output))

    def test_unknown_errors_do_not_echo_arbitrary_values(self):
        self.assertEqual("fixtureFailure categories=UNCLASSIFIED tasks=NONE", fixture_failure_summary("secret-password /private/key.jks token=value"))

    def test_task_output_cannot_smuggle_paths_or_messages(self):
        self.assertEqual("fixtureFailure categories=MEMORY tasks=NONE", fixture_failure_summary("> Task :evil/path FAILED\n> Task :evil token FAILED\nJava heap space"))

if __name__ == "__main__":
    unittest.main()
