import importlib.util
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("verify_release", Path(__file__).parents[1] / "verify_release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class ReleaseVerificationTest(unittest.TestCase):
    def metadata(self, **changes):
        return dict(package=release.PACKAGE, code=100000, name="1.0.0",
                    certificate=release.CERTIFICATE, debuggable=False, abis=release.ABIS) | changes

    def test_valid_release(self):
        release.validate(self.metadata(), 100000, "1.0.0")

    def test_wrong_identity_versions_and_abis_are_rejected(self):
        for changes in [dict(package=release.PACKAGE + ".dev"), dict(certificate="debug"),
                        dict(code=release.BASELINE_CODE), dict(name="other"),
                        dict(debuggable=True), dict(abis={"arm64-v8a"})]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                release.validate(self.metadata(**changes), 100000, "1.0.0")

    def test_previous_release_must_have_same_identity_and_lower_code(self):
        release.validate(self.metadata(), 100000, "1.0.0", self.metadata(code=99999))
        for previous in [self.metadata(), self.metadata(code=100005),
                         self.metadata(certificate="debug"), self.metadata(package="other")]:
            with self.subTest(previous=previous), self.assertRaises(ValueError):
                release.validate(self.metadata(), 100000, "1.0.0", previous)

    @patch.object(release.subprocess, "run", side_effect=subprocess.CalledProcessError(1, "apksigner"))
    def test_unsigned_apk_is_rejected(self, run):
        with self.assertRaises(subprocess.CalledProcessError):
            release.inspect_apk(Path("unsigned.apk"), Path("apksigner"), Path("aapt"))
        self.assertEqual(1, run.call_count)


if __name__ == "__main__":
    unittest.main()
