"""Validate the MuPDF srclib supplied by F-Droid and generate its resources on the build server."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def prepare():
    pin = json.loads((ROOT / "native/mupdf/source.json").read_text())
    source = ROOT / "native/mupdf/source"
    if not source.exists():
        raise ValueError("F-Droid must provide the pinned MuPDF srclib before the build.")
    actual = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
    if actual != pin["revision"]:
        raise ValueError("MuPDF source checkout does not match source.json; do not reuse another version.")
    status = subprocess.check_output(["git", "submodule", "status", "--recursive"], cwd=source, text=True)
    if any(line and line[0] != " " for line in status.splitlines()):
        raise ValueError("MuPDF submodules are missing or differ from their pinned revisions.")
    subprocess.run(["make", "generate"], cwd=source, check=True)


if __name__ == "__main__":
    prepare()
