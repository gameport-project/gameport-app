"""Tests of prepare_release.py and release_notes.py, run on a small repository made for each test.

    python3 -m unittest discover -s scripts -p 'test_*.py'
"""
import contextlib
import io
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import prepare_release as pr  # noqa: E402

BINARIES = {
    "shim/arm64-v8a/libsteamclient.so": b"shim",
    "hook/classes.dex": b"hook",
    "xrlayer/arm64-v8a/libXrApiLayer_gameport.so": b"layer",
    "xrloader/arm64-v8a/libopenxr_loader.so": b"loader",
}
LABELS = {
    "languages": ["en", "fr"],
    "en": {"heading": "## GamePort {version}", "news": "What's new", "install": "Install", "apk": "APK {version} over {previous}",
           "installs_over": "Over {previous}.", "patch_again": "Patch again.", "about": "About."},
    "fr": {"heading": "## GamePort {version}", "news": "Nouveautés", "install": "Installer", "apk": "APK {version} sur {previous}",
           "installs_over": "Sur {previous}.", "patch_again": "Repatcher.", "about": "À propos."},
}


def release(version, previous):
    return {
        "version": version, "previous": previous, "code": pr.code_of(version), "patchNeeded": True,
        "window": {"items": []},
        "notes": [{"id": "s", "title": {"en": "New", "fr": "Nouveau"}, "items": [{"id": "i", "text": {"en": "A", "fr": "B"}}]}],
    }


def write(root, relative, content):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(content, str):
        path.write_text(content, encoding="utf-8")
    else:
        path.write_bytes(content)


class Repo:
    """A repository at version 0.7.1, generation 4, with the release file of 0.7.2 written and committed."""

    def __init__(self):
        self.root = pathlib.Path(tempfile.mkdtemp())
        write(self.root, pr.GRADLE, 'android {\n    defaultConfig {\n        versionCode = 701\n        versionName = "0.7.1"\n    }\n}\n')
        write(self.root, pr.VERSIONING, "object PatchVersioning {\n    const val GENERATION = 4\n\n    const val DEV_REVISION = 3\n}\n")
        for relative, content in BINARIES.items():
            write(self.root, f"{pr.ASSETS}/{relative}", content)
        write(self.root, pr.LOCK, "\n".join(["generation=4"] + pr.checksums(self.root)) + "\n")
        write(self.root, "docs/releases/labels.json", json.dumps(LABELS))
        write(self.root, f"{pr.RELEASES}/0.7.1.json", json.dumps(release("0.7.1", "0.7.0")))
        write(self.root, f"{pr.RELEASES}/0.7.2.json", json.dumps(release("0.7.2", "0.7.1")))
        (self.root / "scripts").mkdir()
        for script in ["release_notes.py", "check_patch_generation.sh"]:
            shutil.copy(HERE / script, self.root / "scripts" / script)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.invalid")
        pr.run_notes(self.root)
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "init")

    def git(self, *args):
        return pr.git(self.root, *args)

    def text(self, relative):
        return (self.root / relative).read_text(encoding="utf-8")

    def change(self, message, relative, content):
        write(self.root, relative, content)
        self.git("commit", "-q", "-am", message)

    def cleanup(self):
        shutil.rmtree(self.root, ignore_errors=True)


class PrepareReleaseTest(unittest.TestCase):
    def setUp(self):
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))  # the script talks; the tests do not need to hear it
        self.repo = Repo()
        self.addCleanup(self.repo.cleanup)
        self.root = self.repo.root

    def test_version_code_follows_the_version(self):
        self.assertEqual(702, pr.code_of("0.7.2"))
        self.assertEqual(10_000, pr.code_of("1.0.0"))
        self.assertEqual(12_345, pr.code_of("1.23.45"))
        with self.assertRaises(pr.Problem):
            pr.code_of("0.7")

    def test_a_release_bumps_the_version_and_writes_the_notes_in_one_commit_with_a_tag(self):
        pr.prepare(self.root, "0.7.2")
        gradle = self.repo.text(pr.GRADLE)
        self.assertIn("versionCode = 702", gradle)
        self.assertIn('versionName = "0.7.2"', gradle)
        self.assertTrue((self.root / "docs/releases/v0.7.2.md").exists())
        self.assertEqual("chore(release): prepare 0.7.2", self.repo.git("log", "-1", "--format=%s"))
        self.assertEqual("v0.7.2", self.repo.git("tag", "--list", "v0.7.2"))
        # The tag is on the commit, and nothing is left over.
        self.assertEqual(self.repo.git("rev-parse", "HEAD"), self.repo.git("rev-list", "-n1", "v0.7.2"))
        self.assertEqual("", self.repo.git("status", "--porcelain"))

    def test_the_commit_carries_no_co_author(self):
        pr.prepare(self.root, "0.7.2")
        self.assertNotIn("Co-Authored-By", self.repo.git("log", "-1", "--format=%B"))

    def test_the_generation_stays_when_the_injected_binaries_did_not_change(self):
        pr.prepare(self.root, "0.7.2")
        self.assertIn("GENERATION = 4", self.repo.text(pr.VERSIONING))
        self.assertIn("DEV_REVISION = 3", self.repo.text(pr.VERSIONING))
        self.assertTrue(self.repo.text(pr.LOCK).startswith("generation=4\n"))

    def test_the_generation_is_raised_and_the_dev_revision_reset_when_a_binary_changed(self):
        self.repo.change("hook", f"{pr.ASSETS}/hook/classes.dex", b"hook, changed")
        pr.prepare(self.root, "0.7.2")
        versioning = self.repo.text(pr.VERSIONING)
        self.assertIn("GENERATION = 5", versioning)
        self.assertIn("DEV_REVISION = 0", versioning)
        self.assertTrue(self.repo.text(pr.LOCK).startswith("generation=5\n"))
        # What the release workflow checks next agrees with it.
        check = subprocess.run(["bash", "scripts/check_patch_generation.sh"], cwd=self.root, capture_output=True, text=True)
        self.assertEqual(0, check.returncode, check.stderr)

    def test_the_check_of_the_workflow_would_refuse_a_changed_binary_without_the_release_script(self):
        self.repo.change("hook", f"{pr.ASSETS}/hook/classes.dex", b"hook, changed")
        check = subprocess.run(["bash", "scripts/check_patch_generation.sh"], cwd=self.root, capture_output=True, text=True)
        self.assertNotEqual(0, check.returncode)

    def test_a_generation_raised_by_hand_is_not_raised_twice(self):
        write(self.root, f"{pr.ASSETS}/hook/classes.dex", b"hook, changed")
        self.repo.change("hook and generation", pr.VERSIONING, "object PatchVersioning {\n    const val GENERATION = 5\n\n    const val DEV_REVISION = 0\n}\n")
        pr.prepare(self.root, "0.7.2")
        self.assertIn("GENERATION = 5", self.repo.text(pr.VERSIONING))
        self.assertTrue(self.repo.text(pr.LOCK).startswith("generation=5\n"))

    def test_a_generation_below_the_recorded_one_is_refused(self):
        write(self.root, f"{pr.ASSETS}/hook/classes.dex", b"hook, changed")
        self.repo.change("oops", pr.VERSIONING, "object PatchVersioning {\n    const val GENERATION = 3\n\n    const val DEV_REVISION = 0\n}\n")
        with self.assertRaisesRegex(pr.Problem, "below the recorded"):
            pr.prepare(self.root, "0.7.2")

    def test_the_first_release_without_a_lock_keeps_the_generation_and_records_it(self):
        (self.root / pr.LOCK).unlink()
        self.repo.git("commit", "-q", "-am", "no lock")
        pr.prepare(self.root, "0.7.2")
        self.assertIn("GENERATION = 4", self.repo.text(pr.VERSIONING))
        self.assertTrue(self.repo.text(pr.LOCK).startswith("generation=4\n"))

    def test_a_version_that_is_not_newer_is_refused(self):
        for version in ["0.7.1", "0.7.0", "0.6.9"]:
            with self.assertRaisesRegex(pr.Problem, "not newer"):
                pr.prepare(self.root, version)

    def test_a_badly_written_version_is_refused(self):
        for version in ["0.7", "v0.7.2", "0.7.2-rc1", "", "a.b.c"]:
            with self.assertRaises(pr.Problem):
                pr.prepare(self.root, version)

    def test_a_missing_release_file_is_refused_and_nothing_changes(self):
        (self.root / f"{pr.RELEASES}/0.7.2.json").unlink()
        self.repo.git("commit", "-q", "-am", "no file")
        with self.assertRaisesRegex(pr.Problem, "does not exist"):
            pr.prepare(self.root, "0.7.2")
        self.assertIn("versionCode = 701", self.repo.text(pr.GRADLE))
        self.assertEqual("", self.repo.git("tag", "--list"))

    def test_a_release_file_that_disagrees_is_refused(self):
        for key, value in [("code", 703), ("version", "0.7.3"), ("previous", "0.7.0")]:
            data = release("0.7.2", "0.7.1")
            data[key] = value
            self.repo.change(f"wrong {key}", f"{pr.RELEASES}/0.7.2.json", json.dumps(data))
            with self.assertRaisesRegex(pr.Problem, key):
                pr.prepare(self.root, "0.7.2")

    def test_a_dirty_tree_a_wrong_branch_and_an_existing_tag_are_refused(self):
        write(self.root, "stray.txt", "x")
        with self.assertRaisesRegex(pr.Problem, "not clean"):
            pr.prepare(self.root, "0.7.2")
        (self.root / "stray.txt").unlink()
        with self.assertRaisesRegex(pr.Problem, "prepared on develop"):
            pr.prepare(self.root, "0.7.2", branch="develop")
        self.repo.git("tag", "v0.7.2")
        with self.assertRaisesRegex(pr.Problem, "already exists"):
            pr.prepare(self.root, "0.7.2")

    def test_a_missing_binary_is_refused(self):
        (self.root / f"{pr.ASSETS}/hook/classes.dex").unlink()
        self.repo.git("commit", "-q", "-am", "gone")
        with self.assertRaisesRegex(pr.Problem, "missing"):
            pr.prepare(self.root, "0.7.2")

    def test_a_dry_run_changes_nothing(self):
        pr.prepare(self.root, "0.7.2", dry_run=True)
        self.assertEqual("", self.repo.git("status", "--porcelain"))
        self.assertEqual("", self.repo.git("tag", "--list"))

    def test_no_git_changes_the_files_and_commits_nothing(self):
        pr.prepare(self.root, "0.7.2", no_git=True)
        self.assertIn("versionCode = 702", self.repo.text(pr.GRADLE))
        self.assertEqual("init", self.repo.git("log", "-1", "--format=%s"))
        self.assertEqual("", self.repo.git("tag", "--list"))

    def test_preparing_twice_is_refused_the_second_time(self):
        pr.prepare(self.root, "0.7.2")
        with self.assertRaises(pr.Problem):
            pr.prepare(self.root, "0.7.2")

    def test_verify_accepts_a_prepared_release_and_refuses_the_rest(self):
        pr.prepare(self.root, "0.7.2")
        pr.verify(self.root, "v0.7.2")
        with self.assertRaisesRegex(pr.Problem, "says 0.7.2"):
            pr.verify(self.root, "v0.7.3")
        with self.assertRaises(pr.Problem):
            pr.verify(self.root, "0.7.2")
        # A note that is not what its release file gives fails the check.
        write(self.root, "docs/releases/v0.7.2.md", "edited by hand\n")
        with self.assertRaises(pr.Problem):
            pr.verify(self.root, "v0.7.2")

    def test_verify_refuses_a_tag_whose_version_was_never_bumped(self):
        with self.assertRaisesRegex(pr.Problem, "says 0.7.1"):
            pr.verify(self.root, "v0.7.2")

    def test_the_command_line_reports_a_problem_as_a_failure_with_a_message(self):
        script = pathlib.Path(__file__).resolve().parent / "prepare_release.py"
        result = subprocess.run([sys.executable, str(script), "0.7.1", "--dry-run"], capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("release:", result.stderr)


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        self.repo = Repo()
        self.addCleanup(self.repo.cleanup)

    def run_notes(self, *args):
        return subprocess.run([sys.executable, "scripts/release_notes.py", *args], cwd=self.repo.root, capture_output=True, text=True)

    def test_the_notes_are_english_then_french_with_the_install_paragraphs(self):
        text = self.repo.text("docs/releases/v0.7.1.md")
        self.assertLess(text.index("### What's new"), text.index("### Nouveautés"))
        self.assertIn("#### New\n- A", text)
        self.assertIn("#### Nouveau\n- B", text)
        self.assertIn("APK 0.7.1 over 0.7.0", text)
        self.assertIn("Over 0.7.0. Patch again.", text)

    def test_the_patch_again_sentence_only_appears_when_games_have_to_be_patched(self):
        data = release("0.7.1", "0.7.0")
        data["patchNeeded"] = False
        write(self.repo.root, f"{pr.RELEASES}/0.7.1.json", json.dumps(data))
        self.run_notes()
        self.assertNotIn("Patch again.", self.repo.text("docs/releases/v0.7.1.md"))

    def test_check_passes_when_up_to_date_and_fails_when_stale_or_missing(self):
        self.assertEqual(0, self.run_notes("--check").returncode)
        write(self.repo.root, "docs/releases/v0.7.1.md", "stale\n")
        self.assertNotEqual(0, self.run_notes("--check").returncode)
        (self.repo.root / "docs/releases/v0.7.1.md").unlink()
        self.assertNotEqual(0, self.run_notes("--check").returncode)

    def test_check_writes_nothing(self):
        write(self.repo.root, "docs/releases/v0.7.1.md", "stale\n")
        self.run_notes("--check")
        self.assertEqual("stale\n", self.repo.text("docs/releases/v0.7.1.md"))

    def test_a_release_that_is_not_out_has_no_note_and_is_not_checked(self):
        # The repository is at 0.7.1: 0.7.2 has its file but no note yet, and that is fine until it is released.
        self.assertFalse((self.repo.root / "docs/releases/v0.7.2.md").exists())
        self.assertEqual(0, self.run_notes("--check").returncode)
        self.run_notes()
        self.assertFalse((self.repo.root / "docs/releases/v0.7.2.md").exists())

    def test_a_note_edited_by_hand_for_a_release_that_is_not_out_is_left_alone(self):
        write(self.repo.root, "docs/releases/v0.7.2.md", "draft\n")
        self.assertEqual(0, self.run_notes("--check").returncode)
        self.run_notes()
        self.assertEqual("draft\n", self.repo.text("docs/releases/v0.7.2.md"))

    def test_a_section_without_a_title_and_an_image_are_written(self):
        data = release("0.7.1", "0.7.0")
        data["notes"] = [{"id": "s", "items": [{"id": "i", "text": {"en": "A", "fr": "B"},
                                                  "image": {"src": "screenshots/x.png", "alt": {"en": "Pic", "fr": "Image"}, "width": 300}}]}]
        write(self.repo.root, f"{pr.RELEASES}/0.7.1.json", json.dumps(data))
        self.run_notes()
        text = self.repo.text("docs/releases/v0.7.1.md")
        self.assertNotIn("####", text)
        self.assertIn('<img src="../screenshots/x.png" alt="Pic" width="300" />', text)
        self.assertIn('alt="Image"', text)


if __name__ == "__main__":
    unittest.main()
