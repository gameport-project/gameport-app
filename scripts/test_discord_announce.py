import unittest

import discord_announce as d

RELEASE = {"window": {"items": [{"text": {"en": "A store.", "fr": "Un store."}}, {"text": {"en": "A price.", "fr": "Un prix."}}]}}
ALWAYS = lambda path: True


class PayloadTest(unittest.TestCase):
    def test_the_title_and_the_points_of_the_news_window(self):
        body = d.payload("0.8.0", release=RELEASE, chosen={"images": [], "intro": None}, exists=ALWAYS)
        embed = body["embeds"][0]
        self.assertEqual("GamePort 0.8.0 is out", embed["title"])
        self.assertEqual("• A store.\n• A price.", embed["description"])
        self.assertTrue(embed["url"].endswith("/releases/tag/v0.8.0"))

    def test_the_text_can_be_french(self):
        body = d.payload("0.8.0", lang="fr", release=RELEASE, chosen={"images": [], "intro": None}, exists=ALWAYS)
        self.assertEqual("GamePort 0.8.0 est sortie", body["embeds"][0]["title"])
        self.assertIn("• Un store.", body["embeds"][0]["description"])

    def test_the_intro_opens_the_text(self):
        body = d.payload("0.8.0", release=RELEASE, chosen={"images": [], "intro": "Big one."}, exists=ALWAYS)
        self.assertTrue(body["embeds"][0]["description"].startswith("Big one.\n\n• A store."))

    def test_the_chosen_pictures_are_read_from_the_tag_and_shown_as_one_gallery(self):
        chosen = {"images": ["docs/screenshots/a.png", "docs/screenshots/b.png", "docs/screenshots/c.png"], "intro": None}
        body = d.payload("0.8.0", release=RELEASE, chosen=chosen, exists=ALWAYS)
        first, *others = body["embeds"]
        self.assertTrue(first["image"]["url"].endswith("/v0.8.0/docs/screenshots/a.png"))
        self.assertEqual(2, len(others))
        # The same link makes Discord group the pictures.
        self.assertTrue(all(e["url"] == first["url"] for e in others))

    def test_no_more_than_four_pictures(self):
        chosen = {"images": [f"docs/screenshots/{i}.png" for i in range(7)], "intro": None}
        self.assertEqual(4, len(d.payload("0.8.0", release=RELEASE, chosen=chosen, exists=ALWAYS)["embeds"]))

    def test_a_picture_that_is_not_in_the_repository_stops_everything(self):
        with self.assertRaises(ValueError):
            d.payload("0.8.0", release=RELEASE, chosen={"images": ["docs/screenshots/nope.png"], "intro": None}, exists=lambda path: False)

    def test_a_version_that_was_not_chosen_for_has_no_pictures(self):
        self.assertEqual({"images": [], "intro": None}, d.choices("9.9.9"))

    def test_a_very_long_text_is_cut_under_what_discord_accepts(self):
        long = {"window": {"items": [{"text": {"en": "x" * 500}} for _ in range(20)]}}
        body = d.payload("0.8.0", release=long, chosen={"images": [], "intro": None}, exists=ALWAYS)
        self.assertLessEqual(len(body["embeds"][0]["description"]), 4096)


if __name__ == "__main__":
    unittest.main()
