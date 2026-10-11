# GamePort

[![CI](https://github.com/gameport-project/gameport-app/actions/workflows/ci.yml/badge.svg)](https://github.com/gameport-project/gameport-app/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/gameport-project/gameport-app?label=release)](https://github.com/gameport-project/gameport-app/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/gameport-project/gameport-app/total?label=downloads)](https://github.com/gameport-project/gameport-app/releases)
[![Stars](https://img.shields.io/github/stars/gameport-project/gameport-app?style=flat&label=stars)](https://github.com/gameport-project/gameport-app/stargazers)

**Your Steam games for Steam Frame, on your Quest, Pico or any Android device.**

GamePort is a free, open-source app that signs in to your Steam account, shows the Android versions of the games you own (and those shared by your Steam family), and takes care of everything else: download, install, launch. Your saves follow you through Steam Cloud, and your play time counts on your account.

<p align="center">
  <a href="https://discord.gg/yWXXAC2AKc"><img alt="Join the Discord" src="https://img.shields.io/badge/Join%20the%20Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://gameport-project.github.io/"><img alt="Browse the games" src="https://img.shields.io/badge/Browse%20the%20games-3C4C7A?style=for-the-badge&logo=data:image/svg%2Bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA1MTIgNTEyIj48cGF0aCBkPSJNMzQ4LjIsMTczIEExMjQsMTI0IDAgMSAwIDM4MCwyNTYgTDI3MiwyNTYiIGZpbGw9Im5vbmUiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSI0OCIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIi8%2BPHBhdGggZD0iTTE5NCwyMTQgTDE5NCwyOTggUTE5NCwzMTIgMjA2LDMwNSBMMjcyLDI2MyBRMjgyLDI1NiAyNzIsMjQ5IEwyMDYsMjA3IFExOTQsMjAwIDE5NCwyMTQgWiIgZmlsbD0iI2ZmZiIgc3Ryb2tlPSIjZmZmIiBzdHJva2Utd2lkdGg9IjgiIHN0cm9rZS1saW5lam9pbj0icm91bmQiLz48L3N2Zz4%3D"></a>
</p>

> GamePort is an independent community project. It is not made by, or affiliated with, Valve, Meta or Pico.

## A look

<table>
<tr><td width="50%"><img src="docs/screenshots/home.png" alt="The home: the new Android versions with their price, then your games" width="100%"></td><td width="50%"><img src="docs/screenshots/game.png" alt="A game of your library: the cover lifted off the artwork, the achievements, size and play time, and the actions" width="100%"></td></tr>
<tr><td align="center"><sub>The home: new Android versions with their price, then your games</sub></td><td align="center"><sub>A game of your library</sub></td></tr>
</table>

## The store: the Android versions of Steam games

Every Steam game that has an Android version, in one place, with what you need to decide.

- **The price, in your currency.** The one your Steam account is billed in, with the share off when it is on sale. The button opens the Steam page: GamePort sells nothing.
- **Its price history.** The lowest price seen, the last sale and a plain verdict, from "Lowest ever seen" to "Better to wait", so you know if it is worth it now.
- **What players say of it on your device**, and Valve's own verdicts for Steam Frame and Steam Deck, next to the languages (text, voices, subtitles) as Steam shows them.
- **Filters that fit on two lines.** VR or flat, installable or coming, owned or not, compatible or not; then languages and genres, and eight ways to sort: by name, rating, release, price, discount.
- **A row of the games on sale that you do not own**, and the new Android versions of the week on the home.

The list of these games is also on the website, to browse from any computer or phone: [every game that has an Android version, with what players report for each kind of device](https://gameport-project.github.io/).

<table>
<tr><td width="50%"><img src="docs/screenshots/store.png" alt="The list of the Android games: filters, a row of the games on sale, and the games with their price" width="100%"></td><td width="50%"><img src="docs/screenshots/store-game.png" alt="The page of a game in the store: its price, the lowest price seen, the last sale, and whether it is worth it" width="100%"></td></tr>
<tr><td align="center"><sub>The list, with the games on sale</sub></td><td align="center"><sub>A game: price, history and verdict</sub></td></tr>
</table>

## Compatibility, reported by players

Players say whether each game works on their kind of device (works, offline only, problems). The table shows it game by game and device by device, and the same mark is on the covers of the store.

<table>
<tr><td width="50%"><img src="docs/screenshots/store-covers.png" alt="The same list as covers, with the price on each" width="100%"></td><td width="50%"><img src="docs/screenshots/compatibility.png" alt="Compatibility: what players report for each game, device by device" width="100%"></td></tr>
<tr><td align="center"><sub>The list as covers</sub></td><td align="center"><sub>The compatibility table</sub></td></tr>
</table>

<details>
<summary>More screens</summary>

<p align="center">
  <img src="docs/screenshots/home-all-games.png" alt="All the games, sorted" width="49%">
  <img src="docs/screenshots/filters.png" alt="The filters: type, status, owner and favorites" width="49%">
</p>
<p align="center">
  <img src="docs/screenshots/settings-appearance.png" alt="The appearance settings: colours, background and covers" width="49%">
  <img src="docs/screenshots/downloads.png" alt="Downloads and installed games" width="49%">
</p>
<p align="center">
  <img src="docs/screenshots/achievements.png" alt="The achievements of a game, with their pictures, in the language of the device" width="49%">
</p>
<p align="center">
  <img src="docs/screenshots/menu.png" alt="Staying on a cover opens its actions: play, update, patch again, settings, favorite, hide" width="49%">
  <img src="docs/screenshots/hidden-games.png" alt="Hidden games, listed in the settings with a button to show each again" width="49%">
</p>
<p align="center">
  <img src="docs/screenshots/saves.png" alt="Saves: this device and Steam Cloud side by side" width="49%">
  <img src="docs/screenshots/controllers.png" alt="Controller mapping for a game" width="49%">
</p>
</details>

## What it does

- **Your library, nicely laid out.** VR and flat games, demos and betas, search, filters, favorites, a "Continue" row with what you played last, and a look you can make yours: cover size, colours, background, language.
- **A store for the Android versions.** Every Steam game that has one, with its price, its price history and what players say of it on your device. See [the store](#the-store-the-android-versions-of-steam-games).
- **One tap to install.** GamePort downloads the game, prepares it so it runs on your device, and installs it. It tells you when a game has a new version.
- **Your Steam account, your saves.** Games run under your own account. Saves are kept in sync with Steam Cloud, and a page per game lets you choose what to restore or send.
- **Play time that counts.** The time you play is added to your Steam account, and only while the game is really on screen: not while the device sleeps.
- **Your controllers, your way.** Games made for the Steam Frame controllers get translated onto yours. You can change the mapping game by game.
- **Made for headsets, friendly to phones and tablets.** On a phone or a tablet, everything that belongs to VR simply disappears.

## Works on

- Meta Quest (2, 3, 3S, Pro)
- Pico (4, 4 Ultra and others)
- Other Android headsets, as long as they run OpenXR games
- Android phones and tablets, for the games that are not VR

## Getting started

1. Download the latest APK from the [releases page](https://github.com/gameport-project/gameport-app/releases/latest) and install it on your device (sideloading).
2. Open it and scan the QR code with the Steam app on your phone to sign in. Your password never goes through GamePort.
3. Pick a game in your library and tap **Install**.
4. Tap **Play**.

The first time a game needs a permission, GamePort explains why before asking.

## Good to know

- **Every game is its own adventure.** These games were not made for your device, so GamePort tests them one by one. The list of what works, what does not and why is in [docs/COMPATIBILITY_TESTING.md](docs/COMPATIBILITY_TESTING.md).
- **Online features may not work** in some games. Single player and saves are the focus today.
- **One game at a time per Steam account.** Steam only allows a single game to be played on an account at once, so starting a game on your headset pauses one running on your PC. You can switch off play-time counting in the settings if you prefer.
- **You must own the game.** GamePort only installs games on your account or shared with your Steam family. The store lists the others, with a link to Steam.
- **Prices are a guide.** They come from Steam's public data, in the currency of your account, and the history from a price-history service; the Steam page has the last word.

## When a game does not work

Open the game's page and press the bug button, or the **Report a problem** button that appears when an install fails. GamePort also points it out on a game that closed right away or crashed.

- **Save the report.** It makes a zip in the Downloads folder of your device. It holds the versions, the device, the game's files, its log and GamePort's, how its last runs ended, and what GamePort did for it.
- **Nothing is sent by GamePort.** You choose what to do with the file: attach it to a [ticket](https://github.com/gameport-project/gameport-app/issues/new?template=game-problem.md), or send it to the developers by any means.
- **Your identity stays out.** Account numbers, e-mail addresses, network addresses and your account name are removed from the text files. The crash report Android keeps, when there is one, is a binary file and is not filtered.
- **It cleans up after itself.** What GamePort keeps for reports is deleted after a week, and when the game is uninstalled. The zips you saved are yours and are never touched.

## Join the community

A friendly place to ask for help, share which games run well on your device, follow new releases and suggest ideas. GamePort lives there together with the Decky plugins and the other small projects of the same author.

[**Join the Discord**](https://discord.gg/yWXXAC2AKc) · questions, bug reports, compatibility reports, new releases

## Your privacy

- Your sign-in stays on your device. It is never sent anywhere except to Steam.
- GamePort shows no ads that track you and collects nothing about you.
- To show the Android versions and the prices, GamePort reads public files of a GitHub repository (about every three hours, and only what changed). It sends nothing about you: GitHub sees your IP address like for any web page. The currency of your Steam account, which picks the prices, stays on your device.
- When it opens, GamePort can look at the latest release page on GitHub to tell you a newer version exists (every 4 hours by default; the delay, or Never, is a setting). It only looks: nothing is downloaded or installed without a tap, and GitHub sees your IP address like for any web page.
- It is free and will stay free: no paid app, no paywall.

## Open source

GamePort is built in the open, with the help of other free projects: a Steam emulation layer based on the [Goldberg Steam Emulator](https://gitlab.com/Mr_Goldberg/goldberg_emulator), ideas and code from [ovrport](https://github.com/ovrport/app), and [JavaSteam](https://github.com/Longi94/JavaSteam) to talk to Steam. Everything we reuse, with its licence, is listed in [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md).

- The past prices of the games come from [IsThereAnyDeal](https://isthereanydeal.com), read once for each game and then kept in [gameport-catalog](https://github.com/gameport-project/gameport-catalog), which also holds the catalog and the community VR ports.
- The part that fakes Steam inside the games lives in its own repository: **gameport-steamworks-shim**.
- Want to build it yourself or lend a hand? See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

Français : [README.fr.md](README.fr.md)
