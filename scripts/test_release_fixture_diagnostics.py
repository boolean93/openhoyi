import unittest
from check_release_configuration import fixture_failure_summary, redact_fixture_output

class FixtureDiagnosticsTest(unittest.TestCase):
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
