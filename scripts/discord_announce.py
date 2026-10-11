#!/usr/bin/env python3
"""Announces a release on Discord, through a channel webhook: a title, what is new, a link to the release, and the pictures that were chosen.

    scripts/discord_announce.py 0.8.0        shows what would be posted (nothing is sent)
    DISCORD_WEBHOOK_URL=... scripts/discord_announce.py 0.8.0     posts it

The text comes from the news of the version (app/src/main/assets/releases/<version>.json). The pictures are chosen by hand, in docs/releases/discord.json:

    {"0.8.0": {"images": ["docs/screenshots/store.png", "docs/screenshots/store-game.png"], "intro": "An optional sentence to open with."}}

A version that is not in that file is announced without pictures. Up to four pictures are shown together, as a gallery; the first is the one under the text.
The pictures are read by Discord from the repository at the tag of the release, so they have to be committed.
"""
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
REPO = "gameport-project/gameport-app"
BLURPLE = 0x5865F2
MAX_IMAGES = 4
MAX_DESCRIPTION = 3800


def choices(version, path=ROOT / "docs/releases/discord.json"):
    """What was chosen for this version: {"images": [...], "intro": str or None}; nothing chosen when the version is not in the file."""
    try:
        table = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        table = {}
    chosen = table.get(version, {})
    return {"images": list(chosen.get("images", [])), "intro": chosen.get("intro")}


def release_file(version):
    return json.loads((ROOT / f"app/src/main/assets/releases/{version}.json").read_text(encoding="utf-8"))


def bullets(release, lang):
    """The short points of the news window of the version, one line each."""
    return [f"• {item['text'][lang]}" for item in release["window"]["items"]]


def payload(version, lang="en", chosen=None, release=None, exists=lambda path: (ROOT / path).is_file()):
    """The body of the webhook. Raises ValueError for a picture that is not in the repository: a typo must not post a broken message."""
    release = release or release_file(version)
    chosen = chosen if chosen is not None else choices(version)
    tag = f"v{version}"
    page = f"https://github.com/{REPO}/releases/tag/{tag}"
    images = chosen["images"][:MAX_IMAGES]
    for path in images:
        if not exists(path):
            raise ValueError(f"the picture {path} is not in the repository")
    intro = chosen.get("intro")
    lines = ([intro, ""] if intro else []) + bullets(release, lang)
    description = "\n".join(lines)[:MAX_DESCRIPTION]
    title = f"GamePort {version} is out" if lang == "en" else f"GamePort {version} est sortie"
    footer = "Free and open source. Not made by or affiliated with Valve, Meta or Pico." if lang == "en" else "Gratuite et open source. Ni créée par Valve, Meta ou Pico, ni affiliée à eux."
    first = {"title": title, "url": page, "description": description, "color": BLURPLE, "footer": {"text": footer}}
    embeds = [first]
    urls = [f"https://raw.githubusercontent.com/{REPO}/{tag}/{path}" for path in images]
    if urls:
        first["image"] = {"url": urls[0]}
        # Embeds that share a link are shown together by Discord, as one gallery.
        embeds += [{"url": page, "image": {"url": url}} for url in urls[1:]]
    return {"username": "GamePort", "embeds": embeds}


def send(url, body):
    request = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"Content-Type": "application/json", "User-Agent": "gameport-release"})
    with urllib.request.urlopen(request, timeout=30) as answer:
        return answer.status


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        sys.exit(__doc__)
    version = args[0].lstrip("v")
    lang = "fr" if "--fr" in sys.argv else "en"
    body = payload(version, lang)
    print(f"{body['embeds'][0]['title']}\n")
    print(body["embeds"][0]["description"])
    print(f"\npictures: {[e['image']['url'].split('/', 6)[-1] for e in body['embeds'] if 'image' in e] or 'none chosen (docs/releases/discord.json)'}")
    url = os.environ.get("DISCORD_WEBHOOK_URL")
    if not url:
        print("\nNothing was sent: DISCORD_WEBHOOK_URL is not set.")
        return
    try:
        print(f"\nsent: {send(url, body)}")
    except urllib.error.HTTPError as error:
        sys.exit(f"Discord answered {error.code}")


if __name__ == "__main__":
    main()
