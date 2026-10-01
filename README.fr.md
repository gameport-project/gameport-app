# GamePort

[![CI](https://github.com/gameport-project/gameport-app/actions/workflows/ci.yml/badge.svg)](https://github.com/gameport-project/gameport-app/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/gameport-project/gameport-app?label=release)](https://github.com/gameport-project/gameport-app/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/gameport-project/gameport-app/total?label=downloads)](https://github.com/gameport-project/gameport-app/releases)
[![Stars](https://img.shields.io/github/stars/gameport-project/gameport-app?style=flat&label=stars)](https://github.com/gameport-project/gameport-app/stargazers)

**Tes jeux Steam Frame sur ton Quest, ton Pico ou n'importe quel appareil Android.**

GamePort est une appli gratuite et open source. Elle se connecte à ton compte Steam, affiche les versions Android des jeux que tu possèdes (et de ceux partagés par ta famille Steam), puis s'occupe de tout : téléchargement, installation, lancement. Tes sauvegardes te suivent grâce au cloud Steam, et ton temps de jeu compte sur ton compte.

> GamePort est un projet communautaire indépendant. Il n'est ni créé par Valve, Meta ou Pico, ni affilié à eux.

## Ce qu'elle fait

- **Ta bibliothèque, bien rangée.** Jeux VR et plats, recherche, favoris, une rangée « Continuer » avec les derniers jeux lancés, et une apparence à ton goût : taille des covers, couleurs, fond, langue.
- **Une pression pour installer.** GamePort télécharge le jeu, le prépare pour qu'il tourne sur ton appareil et l'installe. Elle te prévient quand un jeu a une nouvelle version.
- **Ton compte Steam, tes sauvegardes.** Les jeux tournent sous ton propre compte. Les sauvegardes restent synchronisées avec le cloud Steam, et une page par jeu permet de choisir quoi restaurer ou envoyer.
- **Un temps de jeu qui compte.** Le temps passé s'ajoute à ton compte Steam, et seulement quand le jeu est réellement à l'écran : pas quand l'appareil est en veille.
- **Tes manettes, à ta façon.** Les jeux conçus pour les manettes de la Steam Frame sont traduits sur les tiennes, et le mappage se change jeu par jeu.
- **Pensée pour les casques, sympa sur téléphone et tablette.** Sur un téléphone ou une tablette, tout ce qui touche à la VR disparaît simplement.

## Fonctionne sur

- Meta Quest (2, 3, 3S, Pro)
- Pico (4, 4 Ultra et d'autres)
- D'autres casques Android, tant qu'ils font tourner des jeux OpenXR
- Téléphones et tablettes Android, pour les jeux qui ne sont pas en VR

## Pour commencer

1. Télécharge le dernier APK sur la [page des versions](https://github.com/gameport-project/gameport-app/releases/latest) et installe-le sur ton appareil (installation manuelle).
2. Ouvre-la et scanne le QR code avec l'appli Steam de ton téléphone pour te connecter. Ton mot de passe ne passe jamais par GamePort.
3. Choisis un jeu dans ta bibliothèque et appuie sur **Installer**.
4. Appuie sur **Jouer**.

La première fois qu'un jeu a besoin d'une permission, GamePort explique pourquoi avant de la demander.

## Bon à savoir

- **Chaque jeu est une aventure.** Ces jeux n'ont pas été pensés pour ton appareil, GamePort les teste donc un par un. La liste de ce qui marche, de ce qui ne marche pas et pourquoi est dans [docs/COMPATIBILITY_TESTING.md](docs/COMPATIBILITY_TESTING.md).
- **Les fonctions en ligne peuvent ne pas marcher** dans certains jeux. Le solo et les sauvegardes sont la priorité aujourd'hui.
- **Un seul jeu à la fois par compte Steam.** Steam n'autorise qu'une partie à la fois sur un compte : lancer un jeu sur ton casque met en pause celui qui tourne sur ton PC. Tu peux couper le comptage du temps de jeu dans les réglages si tu préfères.
- **Il faut posséder le jeu.** GamePort ne gère que les jeux de ton compte ou partagés par ta famille Steam.

## Ta vie privée

- Ta connexion reste sur ton appareil. Elle n'est envoyée qu'à Steam.
- GamePort n'affiche aucune publicité qui te suit et ne collecte rien sur toi.
- Elle est gratuite et le restera : pas d'appli payante, pas de mur payant.

## Open source

GamePort se construit au grand jour, avec l'aide d'autres projets libres : une couche d'émulation de Steam basée sur le [Goldberg Steam Emulator](https://gitlab.com/Mr_Goldberg/goldberg_emulator), des idées et du code d'[ovrport](https://github.com/ovrport/app), et [JavaSteam](https://github.com/Longi94/JavaSteam) pour parler à Steam. Tout ce que nous réutilisons, avec sa licence, est listé dans [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md).

- La partie qui simule Steam dans les jeux vit dans son propre dépôt : **gameport-steamworks-shim**.
- Envie de le compiler toi-même ou de donner un coup de main ? Voir [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

English: [README.md](README.md)
