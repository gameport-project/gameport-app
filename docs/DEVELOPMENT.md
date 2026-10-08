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

## Patch versioning

Games record which patch generation patched them, so GamePort can offer to patch again when its own is newer.

- **Release builds** use `PatchVersioning.GENERATION` alone. It is raised when a release is prepared, and only if the patches or the binaries they inject (Steam shim, hook, OpenXR layer) changed since the previous release. It is the only number to touch for that.
- **Debug builds** add a fingerprint of the injected binaries, so during development a rebuilt shim, hook or layer marks earlier games as outdated without raising anything. A change to the patches' own logic, which a fingerprint cannot see, raises `PatchVersioning.DEV_REVISION`; it goes back to 0 when `GENERATION` is raised.

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
