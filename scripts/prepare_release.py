#!/usr/bin/env python3
"""Prepares a release: everything a version needs, in one commit and one tag.

    scripts/prepare_release.py 0.7.2             bump, notes, patch generation, commit "chore(release): prepare 0.7.2", tag v0.7.2
    scripts/prepare_release.py 0.7.2 --dry-run   say what would change, change nothing
    scripts/prepare_release.py 0.7.2 --no-git    change the files, commit and tag nothing
    scripts/prepare_release.py verify v0.7.2     fail unless the repository is a consistent v0.7.2 (what the release workflow runs)

What it does, in this order, and stops at the first thing that is wrong:
  1. checks the version is newer than the current one and that app/src/main/assets/releases/<version>.json exists, agrees with it
     (version, code, previous) and follows the current version;
  2. sets versionCode and versionName in app/build.gradle.kts (the code is major * 10000 + minor * 100 + patch);
  3. raises PatchVersioning.GENERATION by one, and resets DEV_REVISION, when the binaries injected into games differ from the ones recorded for
     the previous release; leaves it alone otherwise, or when it was already raised by hand; then records the state in patch-generation.lock;
  4. writes docs/releases/v<version>.md from the release file (scripts/release_notes.py);
  5. commits those files and tags the commit. Nothing is pushed.
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
GRADLE = "app/build.gradle.kts"
VERSIONING = "core/patch/src/main/kotlin/app/gameport/core/patch/PatchVersioning.kt"
LOCK = "core/patch/patch-generation.lock"
ASSETS = "core/patch/src/main/assets"
RELEASES = "app/src/main/assets/releases"
NOTES = "docs/releases"
# The binaries injected into games: a change in one of them means the games patched before have to be patched again.
BINARIES = [
    "shim/arm64-v8a/libsteamclient.so",
    "hook/classes.dex",
    "xrlayer/arm64-v8a/libXrApiLayer_gameport.so",
    "xrloader/arm64-v8a/libopenxr_loader.so",
]

VERSION = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")
VERSION_CODE = re.compile(r"(versionCode\s*=\s*)(\d+)")
VERSION_NAME = re.compile(r'(versionName\s*=\s*")([^"]*)(")')
GENERATION = re.compile(r"(const val GENERATION = )(\d+)")
DEV_REVISION = re.compile(r"(const val DEV_REVISION = )(\d+)")


class Problem(Exception):
    """Something is wrong; the message says what, and nothing has been changed."""


def parse_version(text):
    match = VERSION.match(text)
    if not match:
        raise Problem(f"'{text}' is not a version such as 0.7.2")
    return tuple(int(part) for part in match.groups())


def code_of(version):
    major, minor, patch = parse_version(version)
    return major * 10_000 + minor * 100 + patch


def read(root, relative):
    return (root / relative).read_text(encoding="utf-8")


def current_version(root):
    text = read(root, GRADLE)
    code, name = VERSION_CODE.search(text), VERSION_NAME.search(text)
    if not code or not name:
        raise Problem(f"versionCode or versionName not found in {GRADLE}")
    return name.group(2), int(code.group(2))


def release_file(root, version):
    path = root / RELEASES / f"{version}.json"
    if not path.exists():
        raise Problem(f"{RELEASES}/{version}.json does not exist: write the news of the version first")
    return json.loads(path.read_text(encoding="utf-8"))


def check_release_file(root, version, previous):
    release = release_file(root, version)
    expected = {"version": version, "code": code_of(version)}
    if previous is not None:
        expected["previous"] = previous
    for key, value in expected.items():
        if release.get(key) != value:
            raise Problem(f"{RELEASES}/{version}.json says {key} {release.get(key)!r}, it must be {value!r}")


def checksums(root):
    out = []
    for relative in BINARIES:
        path = root / ASSETS / relative
        if not path.exists():
            raise Problem(f"{ASSETS}/{relative} is missing: the injected binaries must be staged first")
        out.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {relative}")
    return out


def read_lock(root):
    """The generation and the checksums recorded for the previous release; None before the first one."""
    path = root / LOCK
    if not path.exists():
        return None
    lines = path.read_text(encoding="utf-8").splitlines()
    return int(lines[0].removeprefix("generation=")), lines[1:]


def next_generation(root):
    """(generation to ship, "unchanged", "raised" or "already raised")."""
    match = GENERATION.search(read(root, VERSIONING))
    if not match:
        raise Problem(f"GENERATION not found in {VERSIONING}")
    generation = int(match.group(2))
    lock = read_lock(root)
    if lock is None:
        return generation, "unchanged"
    recorded, recorded_sums = lock
    if checksums(root) == recorded_sums:
        return generation, "unchanged"
    if generation == recorded:
        return generation + 1, "raised"
    if generation > recorded:
        return generation, "already raised"
    raise Problem(f"PatchVersioning.GENERATION ({generation}) is below the recorded one ({recorded})")


def sub_once(pattern, text, replacement, where):
    result, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise Problem(f"{where}: nothing to replace")
    return result


def plan(root, version):
    """Everything that would change, as ({relative path: new text}, generation, what happened to it). Raises Problem when the version cannot be released."""
    name, _ = current_version(root)
    if parse_version(version) <= parse_version(name):
        raise Problem(f"{version} is not newer than the current version {name}")
    check_release_file(root, version, previous=name)
    changes = {}
    gradle = read(root, GRADLE)
    gradle = sub_once(VERSION_CODE, gradle, lambda m: f"{m.group(1)}{code_of(version)}", GRADLE)
    gradle = sub_once(VERSION_NAME, gradle, lambda m: f"{m.group(1)}{version}{m.group(3)}", GRADLE)
    changes[GRADLE] = gradle
    generation, how = next_generation(root)
    if how == "raised":
        versioning = read(root, VERSIONING)
        versioning = sub_once(GENERATION, versioning, lambda m: f"{m.group(1)}{generation}", VERSIONING)
        versioning = sub_once(DEV_REVISION, versioning, lambda m: f"{m.group(1)}0", VERSIONING)
        changes[VERSIONING] = versioning
    lock = "\n".join([f"generation={generation}"] + checksums(root)) + "\n"
    if not (root / LOCK).exists() or read(root, LOCK) != lock:
        changes[LOCK] = lock
    return changes, generation, how


def git(root, *args):
    result = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True)
    if result.returncode != 0:
        raise Problem(f"git {' '.join(args)}: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout.strip()


def run_notes(root, *extra):
    script = root / "scripts" / "release_notes.py"
    result = subprocess.run([sys.executable, str(script), *extra], cwd=root, capture_output=True, text=True)
    if result.returncode != 0:
        raise Problem((result.stderr or result.stdout).strip() or "release_notes.py failed")


def prepare(root, version, dry_run=False, no_git=False, branch="main"):
    tag = f"v{version}"
    if not dry_run and not no_git:
        if git(root, "status", "--porcelain"):
            raise Problem("the working tree is not clean: commit or put aside what is there first")
        if git(root, "rev-parse", "--abbrev-ref", "HEAD") != branch:
            raise Problem(f"a release is prepared on {branch}")
        if git(root, "tag", "--list", tag):
            raise Problem(f"the tag {tag} already exists")
    changes, generation, how = plan(root, version)
    current, _ = current_version(root)
    print(f"version    {current} -> {version} (code {code_of(version)})")
    print(f"generation {generation} ({how}" + (": the injected binaries changed)" if how != "unchanged" else ")"))
    files = sorted(changes) + [f"{NOTES}/{tag}.md"]
    if dry_run:
        print("files      " + ", ".join(files))
        print("dry run: nothing was changed")
        return
    for relative, text in changes.items():
        (root / relative).write_text(text, encoding="utf-8")
    run_notes(root)
    run_notes(root, "--check")
    if no_git:
        print("changed    " + ", ".join(files))
        return
    git(root, "add", "--", *files)
    git(root, "commit", "-q", "-m", f"chore(release): prepare {version}")
    git(root, "tag", "-a", tag, "-m", f"GamePort {version}")
    print(f"committed and tagged {tag}; nothing was pushed")


def verify(root, tag):
    """What the release workflow needs: the repository at [tag] is a consistent release of that version."""
    if not tag.startswith("v"):
        raise Problem(f"'{tag}' is not a tag such as v0.7.2")
    version = tag[1:]
    name, code = current_version(root)
    if name != version or code != code_of(version):
        raise Problem(f"{GRADLE} says {name} (code {code}), the tag says {version} (code {code_of(version)})")
    check_release_file(root, version, previous=None)
    run_notes(root, "--check")
    print(f"{tag} is consistent: version, code, release file and notes agree")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Prepares a release, or verifies a tag.")
    parser.add_argument("target", help="the version to prepare (0.7.2), or 'verify' followed by a tag")
    parser.add_argument("tag", nargs="?", help="the tag to verify (v0.7.2)")
    parser.add_argument("--dry-run", action="store_true", help="say what would change, change nothing")
    parser.add_argument("--no-git", action="store_true", help="change the files but commit and tag nothing")
    parser.add_argument("--branch", default="main", help="the branch a release is prepared on")
    args = parser.parse_args(argv)
    try:
        if args.target == "verify":
            if not args.tag:
                raise Problem("verify needs a tag, such as v0.7.2")
            verify(ROOT, args.tag)
        else:
            prepare(ROOT, args.target, dry_run=args.dry_run, no_git=args.no_git, branch=args.branch)
    except Problem as problem:
        sys.exit(f"release: {problem}")


if __name__ == "__main__":
    main()
