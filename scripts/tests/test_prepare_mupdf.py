import importlib.util
import json
from pathlib import Path
import tempfile
import tomllib
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("prepare_mupdf", Path(__file__).parents[1] / "prepare_mupdf.py")
prepare = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(prepare)


class NativePreparationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "native/mupdf/source").mkdir(parents=True)
        (self.root / "native/mupdf/source.json").write_text(json.dumps({"revision": "pinned", "repository": "official"}))
        patcher = patch.object(prepare, "ROOT", self.root)
        patcher.start()
        self.addCleanup(patcher.stop)

    @patch.object(prepare.subprocess, "run")
    @patch.object(prepare.subprocess, "check_output", side_effect=["pinned\n", " abc submodule\n"])
    def test_preparation_checks_pins_and_only_generates_resources(self, output, run):
        prepare.prepare()
        self.assertEqual(2, output.call_count)
        run.assert_called_once_with(["make", "generate"], cwd=self.root / "native/mupdf/source", check=True)

    @patch.object(prepare.subprocess, "run")
    @patch.object(prepare.subprocess, "check_output", return_value="other-version\n")
    def test_wrong_revision_never_builds(self, output, run):
        with self.assertRaises(ValueError):
            prepare.prepare()
        run.assert_not_called()

    @patch.object(prepare.subprocess, "run")
    def test_does_not_fetch_missing_sources(self, run):
        (self.root / "native/mupdf/source").rmdir()
        with self.assertRaises(ValueError):
            prepare.prepare()
        run.assert_not_called()

    @patch.object(prepare.subprocess, "run")
    @patch.object(prepare.subprocess, "check_output", side_effect=["pinned\n", "-abc missing-submodule\n"])
    def test_missing_submodule_never_builds(self, output, run):
        with self.assertRaises(ValueError):
            prepare.prepare()
        run.assert_not_called()


class ReleasePinTest(unittest.TestCase):
    def test_native_source_catalog_attribution_and_fdroid_use_the_same_version(self):
        root = Path(__file__).resolve().parents[2]
        pin = json.loads((root / "native/mupdf/source.json").read_text())
        catalog = tomllib.loads((root / "gradle/libs.versions.toml").read_text())
        attribution = json.loads((root / "app/config/aboutlibraries-source/libraries/mupdf.json").read_text())
        self.assertEqual(catalog["versions"]["mupdf"], pin["version"])
        self.assertEqual(pin["version"], attribution["artifactVersion"])
        self.assertIn("MuPDF@" + pin["revision"], (root / "fdroid/metadata/com.javierreansyah.pinecone.yml").read_text())

if __name__ == "__main__":
    unittest.main()
