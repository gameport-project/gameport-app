#!/usr/bin/env python3
"""Builds docs/releases/v<version>.md from app/src/main/assets/releases/<version>.json and docs/releases/labels.json.

    scripts/release_notes.py            writes the notes of every release
    scripts/release_notes.py --check    writes nothing, fails when a note is not what its release file gives
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RELEASES = ROOT / "app/src/main/assets/releases"
LABELS = json.loads((ROOT / "docs/releases/labels.json").read_text(encoding="utf-8"))
LANGUAGES = LABELS["languages"]


def language_part(release, lang):
    labels = LABELS[lang]
    version = release["version"]
    out = [labels["heading"].format(version=version), "", f"### {labels['news']}"]
    for section in release["notes"]:
        if "title" in section:
            out += ["", f"#### {section['title'][lang]}"]
        for item in section["items"]:
            out.append(f"- {item['text'][lang]}")
            image = item.get("image")
            if image:
                out += ["", f"  <img src=\"../{image['src']}\" alt=\"{image['alt'][lang]}\" width=\"{image['width']}\" />"]
    paragraphs = [
        labels["apk"].format(version=version, previous=release["previous"]),
        labels["installs_over"].format(previous=release["previous"]) + (" " + labels["patch_again"] if release["patchNeeded"] else ""),
        labels["about"],
    ]
    out += ["", "---", "", f"### {labels['install']}"] + "\n\n".join(paragraphs).split("\n")
    return "\n".join(out)


def notes(release):
    return "\n\n---\n\n".join(language_part(release, lang) for lang in LANGUAGES) + "\n"


def main():
    check = "--check" in sys.argv
    stale = []
    for path in sorted(RELEASES.glob("*.json")):
        release = json.loads(path.read_text(encoding="utf-8"))
        target = ROOT / "docs/releases" / f"v{release['version']}.md"
        text = notes(release)
        if check:
            if not target.exists() or target.read_text(encoding="utf-8") != text:
                stale.append(target.name)
        else:
            target.write_text(text, encoding="utf-8")
    if stale:
        sys.exit("Not what the release files give: " + ", ".join(stale) + " (run scripts/release_notes.py)")


main()
