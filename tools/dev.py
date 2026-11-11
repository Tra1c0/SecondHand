#!/usr/bin/env python3
"""Run the pinned project's checks without committing machine-specific paths."""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]


def environment() -> dict[str, str]:
    env = os.environ.copy()
    local_tools = ROOT.parent / ".toolchains"
    if not env.get("JAVA_HOME"):
        jdks = sorted(local_tools.glob("jdk-17*"))
        if jdks:
            env["JAVA_HOME"] = str(jdks[-1])
    if not env.get("ANDROID_HOME") and (local_tools / "android-sdk").is_dir():
        env["ANDROID_HOME"] = str(local_tools / "android-sdk")
    if not env.get("GRADLE_USER_HOME") and (local_tools / "gradle-user").is_dir():
        env["GRADLE_USER_HOME"] = str(local_tools / "gradle-user")
    if not env.get("ANDROID_HOME"):
        raise SystemExit("Set ANDROID_HOME to an Android SDK with platform 37.0 and NDK 28.0.13004108.")
    java = Path(env["JAVA_HOME"]) / "bin/java" if env.get("JAVA_HOME") else Path("java")
    result = subprocess.run([str(java), "-version"], capture_output=True, text=True)
    if result.returncode or 'version "17.' not in result.stderr:
        raise SystemExit("Set JAVA_HOME to a JDK 17 installation; see docs/BUILD.md.")
    return env


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("check", choices=["build", "upstream-tests", "core-tests", "agent-tests", "lint", "doctor"])
    parser.add_argument("--abi", default="arm64-v8a",
                        choices=["arm64-v8a", "armeabi-v7a", "x86", "x86_64"],
                        help="Native ABI for this development APK")
    args = parser.parse_args()
    env = environment()
    if args.check == "doctor":
        print("JDK 17 available")
        print("SDK:", env["ANDROID_HOME"])
        print("Upstream pin:", (ROOT / "upstream.lock").read_text().splitlines()[-1])
        return 0
    tasks = {
        "build": [":app:assembleDebugNoMinify"],
        "upstream-tests": [":app:testDebugUnitTest"],
        "core-tests": [":agent-core:test"],
        "agent-tests": [":app:testDebugUnitTest", "--tests", "helium314.keyboard.agent.*",
                        "--tests", "helium314.keyboard.latin.InputLogicTest.generated*",
                        "--tests", "helium314.keyboard.latin.inputlogic.BatchDeliveryTest"],
        "lint": [":app:lintDebugNoMinify"],
    }[args.check]
    command = [str(ROOT / "gradlew"), *tasks,
               "-Pandroid.injected.build.abi=" + args.abi,
               "--no-configuration-cache", "--console=plain", "--max-workers=2",
               "-Dorg.gradle.jvmargs=-Xmx2048m"]
    code = subprocess.run(command, cwd=ROOT, env=env).returncode
    if code == 0 and args.check == "build":
        # AGP's development-ABI override chooses one native target. Publish each
        # build separately so a later emulator build cannot overwrite the phone APK.
        output = ROOT / "app/build/intermediates/apk/debugNoMinify"
        sources = list(output.glob("*.apk"))
        if len(sources) != 1:
            raise SystemExit("Build succeeded but its APK location was ambiguous.")
        artifacts = ROOT / "artifacts"
        artifacts.mkdir(exist_ok=True)
        target = artifacts / (sources[0].stem + "-" + args.abi + ".apk")
        shutil.copyfile(sources[0], target)
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        target.with_suffix(".apk.sha256").write_text(digest + "  " + target.name + "\n")
        print("APK:", target)
    return code


if __name__ == "__main__":
    sys.exit(main())
