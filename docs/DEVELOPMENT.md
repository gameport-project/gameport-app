# Developing GamePort

For people who want to build GamePort or contribute. If you only want to use it, the [README](../README.md) is enough.

## Repositories

| Repository | What it is |
|---|---|
| `gameport-app` (this one) | The Android app (Kotlin, Compose): sign-in, library, downloads, patching, saves, settings. Also holds the in-game hook (`hook/`) and the OpenXR layer (`xrlayer/`). |
| `gameport-steamworks-shim` | The native `libsteamclient.so` injected into patched games so their Steamworks calls succeed. A fork of the Goldberg Steam Emulator (LGPLv3); our changes are on the `gameport` branch. |

The two sit side by side in one folder. Scripts find each other from their own location.

## Building

The app builds with Gradle (JDK 17, Android SDK): `./gradlew assembleDebug testDebugUnitTest`.

Three pieces are native or generated and checked in as assets of `core/patch`; rebuild them when their sources change:

- **In-game hook** (Java, compiled to a dex): `ANDROID_HOME=<sdk> scripts/build_hook.sh`
- **OpenXR layer** (C++): `ANDROID_NDK=<ndk> scripts/build_xr_layer.sh` (add `--syntax-only` to just check it)
- **Steamworks shim** (C++, in the other repository), then copy it into this app:
  `ANDROID_NDK=<ndk> GP_PROTOBUF_SRC=<protobuf v35.1 sources> ../gameport-steamworks-shim/android/build_shim.sh && ANDROID_NDK=<ndk> scripts/stage_shim.sh`

`scripts/stage_shim.sh` also rewrites the home-folder part of build paths left inside the binary, and `scripts/check_no_personal_paths.sh` (run by the CI) fails if any shipped binary still carries one.

`scripts/capture_logs.sh` dumps the device's logcat to a file for reading.

## Looking into an install

An install problem on a headset is hard to see: the confirmation Android asks for opens outside the game, the useful
log lines scroll away, and putting the headset on between every try is slow. Three things help.

**A report saves itself.** When an install fails for a reason worth reporting, GamePort writes the whole report
(the game's event log, the system's log, the device's facts, what the download holds) into `Download/` at that moment,
and the failure on the game's page says the file name. Pull it with
`adb pull /sdcard/Download/<name>.zip`, or copy it over USB from the device. The event log inside is the one to read
first: it carries every step of the install, the number the system answered with, and why an APK could not be read.

**A test build answers from a cable.** A debug build carries a receiver (`DebugBridgeReceiver`, `app/src/debug/`) that
reports what GamePort knows and drives an install without the headset being worn:

| Command | What it answers |
|---|---|
| `scripts/install_debug.sh state` | Where every install stands, with the package of each installed game |
| `scripts/install_debug.sh perm` | Whether the system lets GamePort install apps and write all files, and how it stands with Steam |
| `scripts/install_debug.sh storage` | Tries, from inside the app, to create and write a game's expansion folder, and says what the system answered |
| `scripts/install_debug.sh log <appId>` | Everything GamePort did with that game |
| `scripts/install_debug.sh files <appId>` | What its download holds, and what each APK says about itself |
| `scripts/install_debug.sh install <appId>` | Starts the install |
| `scripts/install_debug.sh cancel <appId>` | Stops the one running |
| `scripts/install_debug.sh discard <appId>` | Throws its download away |

It is in the test build only, and its package name (`app.gameport.dev`) is its own: a release has no such receiver,
which `./gradlew :app:processReleaseMainManifest` can be checked against.

**The rest is plain `adb`.** Much of what matters needs nothing added to the app:

```sh
adb logcat -s GamePortDebug:I PackageInstaller:* PackageManager:*  # what the installer itself says
adb shell dumpsys package app.gameport.dev | grep -A3 'requested permissions' # what GamePort was granted
adb shell appops get app.gameport.dev REQUEST_INSTALL_PACKAGES                # whether it may install unknown apps
adb shell pm list packages -3                                                 # the games on the device
adb shell run-as app.gameport.dev cat files/game-events.log                   # the event log, test build only
adb shell ls -l /sdcard/Android/obb/<game package>                            # the game's large files
```

What `adb` cannot do well on a headset is drive the screen: `uiautomator dump` does not see an immersive surface, and
`input tap` lands on coordinates that do not map to what the player sees. That is why the receiver above acts on the
install rather than on the UI.

## Patch versioning

Games record which patch generation patched them, so GamePort can offer to patch again when its own is newer.

- **Release builds** use `PatchVersioning.GENERATION`, plus `PatchVersioning.DEV_REVISION` (0 in a release, above 0 in an alpha built before the next release). `GENERATION` is raised when a release is prepared, and only if the patches or the binaries they inject (Steam shim, hook, OpenXR layer) changed since the previous release. It is the only number to touch for that.
- **Debug builds** add a fingerprint of the injected binaries, so during development a rebuilt shim, hook or layer marks earlier games as outdated without raising anything. A change to the patches' own logic, which a fingerprint cannot see, raises `PatchVersioning.DEV_REVISION` (an alpha then patches again the games a release patched); it goes back to 0 when `GENERATION` is raised.

### The debug key

Debug builds (yours and the CI's) are all signed with `app/debug.keystore`, a debug key made for this project, public on purpose (password `android`), so they update one another on a device. It is no secret and signs nothing but test builds; it is not the debug key of anyone's machine. Releases use the private key described below, which is why a release does not update over a debug build.

## Continuous integration and releases

- **CI** (`.github/workflows/ci.yml`) runs on every push to `main` and every pull request: it builds the debug APK, runs the unit tests, compiles the in-game hook, checks the syntax of the OpenXR layer and scans the history for secrets and personal data.
- **Releases** (`.github/workflows/release.yml`) build the release APK, sign it with the key kept in the repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and publish it as a GitHub release, which is also what the download counter counts. They start from Actions > Release > Run workflow (see below) or when a tag such as `v0.2.0`, prepared locally, is pushed.
- **Releasing a version:**
  1. Write `app/src/main/assets/releases/<version>.json` (the news window and the release notes are both built from it; `version`, `code` and `previous` must agree with the version) and merge it into `main`.
  2. Run Actions > Release > Run workflow with the version. The workflow prepares the release (`scripts/prepare_release.py`: `versionCode` and `versionName`, `docs/releases/v<version>.md`, `PatchVersioning.GENERATION` raised and `DEV_REVISION` reset when the injected binaries changed since the previous release, `patch-generation.lock` recorded), builds and signs the APK, and only then pushes the commit `chore(release): prepare <version>` and the tag `v<version>` to `main` and publishes. If anything fails, nothing is pushed. The `main` branch must let the workflow push to it.
  3. Or do the preparation yourself: `scripts/prepare_release.py <version>` on a clean `main` (add `--dry-run` to see what it would change) makes the same commit and tag locally and pushes nothing; push `main` and the tag, and the workflow checks them (`scripts/prepare_release.py verify v<version>`) and publishes.

  The notes under `docs/releases/` from 0.7.0 on are written by `scripts/release_notes.py` and never by hand; the CI fails when one is not what its release file gives. The release workflow refuses a tag when the binaries changed and the generation did not.

### Keeping the signing key safe

The key is random and is never in the repository: anyone can run `scripts/make_release_key.sh`, but that makes *their own* new key, which Android treats as a different publisher and which cannot update the real one. What must stay private is the key's folder and the four repository secrets. In the repository settings:

- create the **`release` environment** (Settings > Environments) and require an approval from a maintainer before the job runs, with the four secrets stored in it;
- protect the **`v*` tags** (Settings > Rules) so only maintainers can create them: the release job only runs on those tags;
- secrets are not given to workflows run for pull requests from forks.

## Rules

- No personal data (SteamID, names, tokens) in code, scripts or docs. The app writes the current user's public identity for each game right before launching it.
- Code copied from a third-party project gets a header naming its origin and licence, and an entry in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- Only games the user legitimately owns are handled.
- Texts shown to the user are written for any device ("this device", not "headset") and in a general form, without addressing the reader.
- Every scrolling page keeps a bottom margin equal to the top one.
