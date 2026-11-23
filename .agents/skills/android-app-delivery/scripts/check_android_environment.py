#!/usr/bin/env python3
"""Read-only Android toolchain inventory. Does not build, install or contact devices."""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


def property_value(project, key):
    path = project / "local.properties"
    if not path.is_file():
        return None
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        match = re.match(r"^\s*" + re.escape(key) + r"\s*[=:]\s*(.*?)\s*$", line)
        if match:
            escapes = {"t": "\t", "n": "\n", "r": "\r", "f": "\f"}
            return re.sub(
                r"\\u([0-9a-fA-F]{4})|\\(.)",
                lambda m: chr(int(m[1], 16)) if m[1] else escapes.get(m[2], m[2]),
                match[1],
            )
    return None


def directory_names(root, child):
    path = root / child
    return sorted(p.name for p in path.iterdir() if p.is_dir()) if path.is_dir() else []


def inventory(project, sdk_override=None, java_override=None):
    issues = []
    notes = []
    java_home = java_override or os.environ.get("JAVA_HOME")
    java = str(Path(java_home).expanduser() / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else shutil.which("java")
    java_version = None
    if not java or not Path(java).is_file():
        issues.append("Java executable not found; check JAVA_HOME or PATH.")
    else:
        try:
            result = subprocess.run([java, "-version"], capture_output=True, timeout=15)
            output = (result.stderr + result.stdout).decode("utf-8", errors="replace").strip()
            java_version = output.splitlines()[:3]
            if result.returncode:
                issues.append("java -version returned a nonzero exit code.")
        except (OSError, subprocess.TimeoutExpired) as exc:
            issues.append("Java probe failed: " + type(exc).__name__)

    local_sdk = property_value(project, "sdk.dir") if not sdk_override else None
    sdk_value = sdk_override or local_sdk or os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    sdk = Path(sdk_value).expanduser() if sdk_value else None
    if sdk:
        sdk = ((project / sdk) if local_sdk and not sdk.is_absolute() else sdk).resolve()
    sdk_source = "--sdk" if sdk_override else "local.properties" if local_sdk else "ANDROID_HOME" if os.environ.get("ANDROID_HOME") else "ANDROID_SDK_ROOT" if sdk_value else None
    platforms = directory_names(sdk, "platforms") if sdk and sdk.is_dir() else []
    build_tools = directory_names(sdk, "build-tools") if sdk and sdk.is_dir() else []
    if not sdk or not sdk.is_dir():
        issues.append("Android SDK directory not found.")
    elif not platforms or not build_tools:
        issues.append("SDK platforms or build-tools are missing; select versions required by the project.")

    wrapper_files = ["gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties"]
    wrapper = {name: (project / name).is_file() for name in wrapper_files}
    settings = [name for name in ("settings.gradle.kts", "settings.gradle") if (project / name).is_file()]
    if not settings:
        notes.append("No Gradle settings found; this may be a new, uninitialized project.")
    if not all(wrapper.values()):
        notes.append("Gradle Wrapper is incomplete; initialize or repair it before building.")
    adb_name = "adb.exe" if os.name == "nt" else "adb"
    adb = sdk / "platform-tools" / adb_name if sdk else None
    return {
        "project": str(project), "java_executable": java, "java_version": java_version,
        "sdk": str(sdk) if sdk else None, "sdk_source": sdk_source,
        "sdk_platforms": platforms, "build_tools": build_tools,
        "adb": str(adb) if adb and adb.is_file() else shutil.which("adb"),
        "gradle_settings": settings, "wrapper_files": wrapper,
        "issues": issues, "notes": notes,
        "compatibility_verified": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path.cwd())
    parser.add_argument("--sdk", help="Override SDK location without changing configuration.")
    parser.add_argument("--java-home", help="Override JDK location for this probe only.")
    args = parser.parse_args()
    project = args.project.expanduser().resolve()
    if not project.is_dir():
        parser.error("Project directory does not exist.")
    try:
        report = inventory(project, args.sdk, args.java_home)
    except (OSError, UnicodeError) as exc:
        print(json.dumps({"error": "Could not read environment: " + type(exc).__name__}))
        return 2
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 1 if report["issues"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
