"""Verify an optional developer-signed APK; F-Droid signs its own releases with a different key."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.javierreansyah.pinecone"
CERTIFICATE = "20027d393fa7085ecaf8df2e598d2cf54ee914969d85ba8688e8668c79597727"
BASELINE_CODE = 89028166
ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}


def source_version():
    text = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    return int(re.search(r'versionCode\s*=\s*(\d+)', text)[1]), re.search(
        r'versionName\s*=\s*"([^"]+)"', text)[1]


def sdk_tools(sdk):
    sdk = sdk or os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        local = ROOT / "local.properties"
        if local.exists():
            match = re.search(r"^sdk.dir=(.+)$", local.read_text(), re.MULTILINE)
            if match:
                sdk = match[1].replace("\\:", ":").replace("\\\\", "\\").strip()
    if not sdk:
        raise ValueError("Set ANDROID_HOME or pass --sdk.")
    tools = Path(sdk) / "build-tools/37.0.0"
    return tools / ("apksigner.bat" if os.name == "nt" else "apksigner"), tools / (
        "aapt.exe" if os.name == "nt" else "aapt")


def inspect_apk(apk, signer, aapt):
    signatures = subprocess.run([str(signer), "verify", "--print-certs", str(apk)],
                               check=True, capture_output=True, text=True).stdout
    certificates = re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", signatures)
    if len(certificates) != 1:
        raise ValueError("Expected exactly one verified APK signer.")
    badging = subprocess.run([str(aapt), "dump", "badging", str(apk)],
                            check=True, capture_output=True, text=True).stdout
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package:
        raise ValueError("Cannot read APK package/version metadata.")
    abi_line = re.search(r"^native-code: (.+)$", badging, re.MULTILINE)
    return dict(package=package[1], code=int(package[2]), name=package[3],
                certificate=certificates[0].lower(), debuggable="application-debuggable" in badging,
                abis=set(re.findall(r"'([^']+)'", abi_line[1])) if abi_line else set())


def validate(metadata, expected_code, expected_name, previous=None):
    if metadata["package"] != PACKAGE:
        raise ValueError("Wrong production package ID.")
    if metadata["certificate"] != CERTIFICATE:
        raise ValueError("Wrong release certificate; this APK cannot update existing public releases.")
    if metadata["debuggable"]:
        raise ValueError("Distribution APK is debuggable.")
    if metadata["code"] != expected_code or metadata["name"] != expected_name:
        raise ValueError("APK version differs from the committed Gradle version.")
    if metadata["abis"] != ABIS:
        raise ValueError("Distribution APK must retain all four supported native ABIs.")
    floor = BASELINE_CODE
    if previous:
        if previous["package"] != PACKAGE or previous["certificate"] != CERTIFICATE:
            raise ValueError("Previous APK has a different package or release certificate.")
        floor = max(floor, previous["code"])
    if metadata["code"] <= floor:
        raise ValueError("Release versionCode must exceed the previous public release.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--previous", type=Path, help="Previous public release APK; required after baseline versionCode 100000000")
    parser.add_argument("--sdk", help="Android SDK directory (build-tools 37.0.0)")
    parser.add_argument("--tag", help="Require an exact vMAJOR.MINOR.PATCH release tag")
    args = parser.parse_args()
    code, name = source_version()
    if args.tag is not None and args.tag != f"v{name}":
        raise ValueError("Release tag differs from versionName.")
    if code != 100000000 and args.previous is None:
        raise ValueError("Pass --previous for releases after baseline versionCode 100000000.")
    signer, aapt = sdk_tools(args.sdk)
    metadata = inspect_apk(args.apk, signer, aapt)
    previous = inspect_apk(args.previous, signer, aapt) if args.previous else None
    validate(metadata, code, name, previous)
    with args.apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    print(json.dumps({**metadata, "abis": sorted(metadata["abis"]), "sha256": digest}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"Release verification failed: {error}", file=sys.stderr)
        sys.exit(1)
