# GamePort — Suivi des tests de compatibilité

Chaque entrée : jeu testé, ce qui a été observé, la cause identifiée, le patch appliqué (si applicable), le statut.

Setup de test : Quest 3 / Horizon OS 14, jeux téléchargés et installés via le mécanisme Steam-Android de GameNative (branche `feat/android-steam-version`), patchs appliqués via `overportcli` (ovrport) tournant sur Mac.

---

## SUPERHOT VR (AppID 617830, package `com.superhotgame.superhotvr`)

**Sans patch** : fenêtre 2D plate s'affiche puis disparaît, jamais immersif.
**Cause** : manifest ne déclare que `LAUNCHER` comme catégorie sur l'activity de lancement — manque `com.oculus.intent.category.VR` et `org.khronos.openxr.intent.category.IMMERSIVE_HMD`. Horizon OS traite donc l'app comme une app 2D classique et refuse l'accès au tracking tête/mains (confirmé via logcat : `Client should have focus but doesn't`, `MemoryBroker: HEAD_TRACKER ... does not have access`).

**Patch appliqué** : `patch_launcher_entry` (ovrport) — ajoute les catégories VR/OpenXR manquantes sur l'intent-filter. Temps de patch mesuré : 1m18s pour un APK de ~650 Mo (inclut le téléchargement de Gradle, donc probablement plus rapide en réel).

**Après patch** : passe bien en immersif (`UiModeController: Switching ... immersiveAppPackageName = com.superhotgame.superhotvr`), session OpenXR atteint l'état `FOCUSED` (pleinement fonctionnelle). Puis, ~6s après, le jeu ferme proprement sa session OpenXR et quitte.

**Cause du quit** (confirmée par log applicatif, pas une supposition) :
```
com.valvesoftware.steam_api: dlopen failed: library "libsteamclient.so" not found
[Steamworks.NET] SteamAPI_Init() failed: 'Could not determine Steam client install directory.'
Failed to initialize user system: LibLoadingFailed
```
Le jeu utilise Steamworks.NET et appelle `SteamAPI_Init()` au démarrage, qui cherche un vrai client Steam installé (`libsteamclient.so`). Absent sur le casque → échec → le jeu se ferme lui-même (pas un crash, un arrêt propre).

**Avancement Steamworks (2026-09-29)** : avec notre `libsteamclient.so` injecté dans l'APK, `SteamAPI_Init()` charge maintenant le shim (`Loaded local 'libsteamclient.so' OK`). Nouvelle erreur : `No appID found` — le jeu attend l'AppID normalement fourni par le client Steam (617830 pour SUPERHOT VR). Le shim la définira au chargement (table temporaire package → AppID ; à terme fournie par le lanceur). Identité Steam toujours fictive (pas encore de pont vers la vraie session).

**Résolution (2026-09-29, fin de journée)** : le jeu **fonctionne** (confirmé par le testeur, casque sur la tête). Trois corrections ont été nécessaires après le chargement du shim :
1. AppID fourni au chargement (table package → AppID dans le shim, 617830 ici).
2. `ISteamClient` : le jeu utilise `SteamClient023`, dont la table d'appels est **décalée** par rapport à la 020 du shim (`RunFrame` en case 19, plusieurs méthodes retirées). Servir l'objet 020 faisait planter `SteamAPI_Init` en natif. Ajout d'une vraie interface `ISteamClient023`, générée depuis l'en-tête officiel du SDK récent (miroir `rlabrecque/SteamworksSDK`).
3. `SteamTimeline` (`STEAMTIMELINE_INTERFACE_V004`) : interface récente inconnue du shim ; Steamworks.NET exige que **toutes** les interfaces soient non nulles. Le shim sert un objet dont toutes les méthodes ne font rien et renvoient 0.

**Statut** : ✅ Démarre et tourne avec le patch manifest + shim + AppID. Restent **non traités** : identité Steam toujours fictive (pas de pont vers le vrai compte), sauvegardes/Cloud non branchés, et 4 interfaces à table décalée par rapport au shim (`SteamFriends018`, `SteamUserStats013`, `SteamUGC021`, `SteamRemotePlay004`) qui ne posent problème que si le jeu appelle leurs méthodes.

---

## Arizona Sunshine (package `com.vertigogames.azs1hd`)

**Sans patch** : entre en VR, affiche le nom + chargement, puis quitte après quelques secondes (contrairement à SUPERHOT, celui-ci était déjà tagué VR correctement dans son manifest : `com.oculus.intent.category.VR` présent nativement).

**Piste identifiée mais non confirmée** : déclare la permission `com.pvr.tobactivate.permission.AUTH_CHECK`, spécifique à Pico VR — suggère un build partagé avec une version Pico, qui ferait un check d'activation Pico au démarrage, inexistant sur Quest.

**Statut** : ❓ Non résolu. Pas encore vérifié si le même souci Steamworks (`libsteamclient.so`) s'ajoute à la permission Pico comme cause. À re-tester avec les mêmes logs détaillés que SUPERHOT une fois le shim Steam en place.

---

## Ancient Dungeon

**Sans aucun patch** : se lance directement, l'app entre en VR, **les contrôles fonctionnent**, le jeu est jouable. Affiche un message "souci d'API Steam" dans le menu (même cause que SUPERHOT — `SteamAPI_Init()` qui échoue — mais ce jeu gère l'échec proprement au lieu de se fermer).

**Sauvegardes** : pas encore de mécanisme de sync (attendu, pas encore implémenté côté GamePort).

**Statut** : ✅ Meilleur résultat à ce jour — jouable tel quel, aucun patch nécessaire. Confirme que le problème Steamworks n'est pas toujours fatal, dépend de comment chaque jeu gère l'échec.

---

## Underdogs (AppID 2441700, package `com.onehamsa.underdogs`)

- **Patch GamePort** : le shim se charge, l'AppID et le SteamID du compte connecté sont bien lus depuis l'APK (`appid: 2441700`).
- **Cause du plantage (résolue, 2026-09-30)** : le jeu redemande la permission de stockage (`READ_EXTERNAL_STORAGE`) à chaque reprise. Sur le casque, la demande s'ouvre dans une fenêtre qui prend le focus : le jeu passe en pause, sa taille d'écran tombe à 0 et il demande au runtime des swapchains 0×0 (`xrCreateSwapchain` → `XR_ERROR_VALIDATION_FAILURE`), puis il se ferme. Sans le shim le jeu restait sur son écran d'erreur Steam et ne créait jamais ces swapchains. Vérifié : `pm grant` de la permission fait fonctionner le jeu patché.
- **Correction** : un patch ne peut pas accorder la permission. Retirer sa déclaration du manifeste n'aide pas (Android ouvre quand même l'écran de demande et elle ne peut plus être accordée). Avec `targetSdk` 36 la demande n'affiche aucune fenêtre : elle est refusée d'office. GamePort explique donc au premier « Jouer » qu'il faut autoriser « Fichiers et médias » (bouton vers les permissions du jeu, qui existe aussi dans les réglages du jeu). Vérifié sur Quest : une fois accordée à la main, le jeu tient.
- **Le choix du module « SteamFrame »** est fait par le jeu lui-même, sans patch aussi : ce n'était pas la cause.
- **Couche OpenXR** : elle corrige en plus les swapchains 0×0, les rectangles vides et les quads factices refusés (`xrEndFrame` → `XR_ERROR_LAYER_INVALID`) ; utile pour d'autres jeux Unity construits pour le Steam Frame.
- **Statut** : résolu avec la permission accordée ; l'invite au premier « Jouer » est à vérifier.

## Arizona Sunshine VR Remake (AppID 2897700, package `com.vertigogames.azs1hd`)

- **Fonctionne** (2026-09-30) : le jeu se lance, l'affichage est correct, et **les manettes marchent** grâce à la traduction du profil Valve vers le profil Touch (patch v10+). Le bouton Start marche sur le bouton menu de la manette gauche (le menu droit de la manette Valve y est proposé aussi).
- **Boutons de la manette Valve sans équivalent Touch** (ignorés) : croix directionnelle, gâchettes latérales, bouton « vue », X/Y de droite, `squeeze/touch`. Une future page de mapping par jeu est notée (mémoire du projet).
- **Multijoueur : non résolu.** Le jeu appelle `AuthenticationAPI-AuthenticateSteam` (fonction Firebase du studio) avec `hexEncodedSessionTicket` = ticket `GetAuthSessionTicket`. Avec le faux ticket du shim le serveur répond 500. Avec un **vrai ticket** fabriqué par GamePort (jeton de connexion + en-tête de session de 24 octets + ticket de propriété, 234 octets, déclaré à Steam par `ClientAuthList`, acquitté par Steam) il répond toujours 500 « Internal Server Error », y compris pour une requête complète et valide envoyée à la main (`createAccountIfNotExists: false`, avec les six champs `userSessionDetails`). Le serveur plante donc après la validation des arguments, au moment de vérifier le ticket. Cause inconnue : ticket refusé par Valve, ou fonction qui plante pour autre chose.
- Essayé sans effet : marquer le compte « en jeu » (`ClientGamesPlayed`) pendant que le jeu tient un ticket.

## Space Pirate Trainer (AppID 418650, package `com.iillusions.spacepiratetrainerframe`)

- **Fonctionne parfaitement** (2026-10-02, Quest 3) : téléchargé et installé par GamePort, puis lancé, sans défaut relevé.

## Richie's Plank Experience

- **Fonctionne** (2026-10-02) : retour d'un utilisateur, non testé par nous.

## Cubism VR

- **Fonctionne** (2026-10-02, Quest 3).

## The Last Clockwinder (AppID 1755100, package `com.asg.clockworkdev`)

- **Fonctionne** (2026-10-03, Quest 3). Lancé depuis GamePort : la couche OpenXR de GamePort est chargée, la swapchain est créée à 1680x1760 par œil et la boucle de rendu tourne, sans erreur du chargeur OpenXR.
- Son chargeur OpenXR est récent : le patch `xr_loader` n'y touche pas.

## Moss 2

- **Fonctionne** (2026-10-03, Quest 3), image, son et manettes, depuis le remplacement du chargeur OpenXR.
- Cause du problème : le jeu embarque un ancien chargeur OpenXR (compilé avec le NDK r21, sans la chaîne `LoaderInitData not initialized`). Sur le Quest il ne trouve pas le runtime : il interroge les courtiers Khronos (« Null cursor »), puis lit `/odm/etc/openxr/1/active_runtime.aarch64.json`, dont la bibliothèque est dans un APK (`...VrDriver.apk!/lib/...`) et que cet ancien chargeur déclare inexistante. Le jeu démarre alors sans VR : le son joue, l'écran de lancement de Meta reste.
- Correction : le patch `xr_loader` remplace le chargeur du jeu par celui de Khronos (1.1.63) quand il n'a pas cette chaîne. Les huit autres jeux de la bibliothèque ont un chargeur récent et ne sont pas touchés.
- Piste écartée : déclarer les permissions OpenXR et les courtiers dans le manifeste ne changeait rien (les courtiers répondent « Null cursor » même sur un jeu qui fonctionne).

## Escape Simulator (AppID 1435790, package `com.PineStudio.EscapeSimulator`)

- **Fonctionne** (2026-10-04, Quest 3), Steam initialisé, image, son et suivi de la tête.
- Jeu Unity avec un fichier d'extension (`main.<code>.<paquet>.obb`) qui contient entre autres les réglages de FMOD. Unity le cherche sous le code de version **installé**, que le patch relève : sans renommage, l'OBB n'est pas monté, FMOD ne trouve pas ses réglages et le jeu plante. GamePort renomme donc les fichiers d'extension après chaque patch, pour Unity comme pour Unreal.
- Les fichiers posés par GamePort doivent être lisibles par le jeu (mode ouvert à tous), sinon « Unable to open archive file ». GamePort les ouvre à la pose et à chaque réalignement.
- Unity ignore l'OBB sans la permission de lecture du stockage, que Android 13+ ne propose plus pour une cible 33 ou plus : le patch `storage_target` abaisse la cible à 32.

## Non pris en charge : jeux qui vérifient l'achat auprès de Meta

- **Metro Awakening** (2026-10-03) et **Vail** (2026-10-04) vérifient l'achat auprès de Meta au lancement. Sans licence du Horizon Store, Vail affiche un avertissement à chaque lancement, Metro quitte quelques secondes après avoir reçu le focus.
- Les fichiers de ces jeux sont complets (Vail : les 156 paks de son manifeste sont présents) : le problème n'est pas l'installation. GamePort ne contourne pas cette vérification, ces jeux ne sont pas pris en charge.

## Tickets Steam pour les services en ligne des jeux

**État (2026-09-30) : les services en ligne des jeux refusent le ticket fabriqué par GamePort. Non supporté.**

- `GetAuthSessionTicket` (Arizona Sunshine, serveur Firebase du studio) : ticket de 234 octets, format identique à celui du vrai client Steam (comparé sur un ticket du PC), déclaré à Steam (`ClientAuthList`, acquitté), compte marqué en jeu, ticket neuf à chaque demande, réponse en environ 1 s, `clientInstanceId` dans l'en-tête. Le serveur répond « Internal Server Error » (500), y compris pour une requête complète et valide envoyée à la main.
- `GetAuthTicketForWebApi` (Ancien Donjon, Unity Gaming Services) : le jeu reçoit le ticket (« Steam Web API auth ticket received (length 234) ») et le service d'Unity répond `401 invalid token / PERMISSION_DENIED`.
- Deux services indépendants refusent le ticket : c'est la validation par Valve (`ISteamUserAuth/AuthenticateUserTicket`) qui échoue, pas un cas propre à un jeu.
- Fichiers de la chaîne : shim `dll/base.cpp` (`getTicket`), `dll/steam_user.h` (`GetAuthTicketForWebApi`), `dll/gameport_launcher_config.cpp` (`gameport_read_ticket`) ; hook `GamePortHookProvider.java` (`watchTicketRequests`) ; GamePort `CloudProvider.kt` (`ticket`), `AuthTicket.kt`, `SteamSession.kt`.
- Différences connues avec un ticket réel, non corrigeables avec JavaSteam : l'adresse locale du ticket de propriété (`127.0.0.1` au lieu de l'adresse du réseau local). Piste restante : un pont vers un vrai client Steam (un PC allumé sur le même réseau), qui n'est pas un produit pour tout le monde.

## Problème transversal : initialisation Steamworks (`SteamAPI_Init` / `libsteamclient.so`)

Touche potentiellement la majorité des jeux Steamworks (SUPERHOT le ferme, Ancient Dungeon l'affiche en warning sans planter — comportement variable par jeu). Root cause : les jeux appellent l'API Steamworks native en s'attendant à un vrai client Steam installé sur la machine, qui n'existe pas sur le casque.

**Piste de résolution** : fournir un shim `libsteamclient.so`/`libsteam_api.so` que le jeu peut charger, qui répond aux appels Steamworks de base (`SteamAPI_Init`, `ISteamUser::BLoggedOn`, `GetSteamID`, etc.) — idéalement en se branchant sur la session Steam déjà authentifiée par GameNative (vraie vérification de possession, pas un contournement type "toujours dire oui" à la Goldberg Emulator) plutôt qu'un stub qui fake tout localement.

**Statut** : 🔴 Non commencé. Priorité haute — si résolu, débloque probablement la majorité des jeux testés jusqu'ici.

**Recherche (2026-09-29)** : le Steam Frame est sorti le 18/09/2026 ; le SDK Steamworks officiel (1.63+) inclut bien des libs Android ARM64 pour `libsteam_api.so` (la lib redistribuable que les jeux embarquent — cohérent avec ce qu'on observe, elle se charge sans problème). Mais sur le vrai Steam Frame, `libsteamclient.so` est fourni par un vrai service Steam tournant en fond sur SteamOS — rien d'équivalent n'existe sur Horizon OS (Quest), et Valve ne documente pas publiquement cette interface privée. Aucun portage Android existant trouvé pour un shim de ce type ; la référence habituelle pour cette ABI reverse-engineered est Goldberg Steam Emulator (Linux/Windows uniquement à ce jour, pas de portage Android connu). Prochaine étape : construire un shim minimal `libsteamclient.so` pour Android arm64, branché sur la session Steam réelle de GameNative plutôt qu'une fausse validation locale.
