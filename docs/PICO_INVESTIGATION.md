# GamePort on a PICO 4: investigation notes


## The device

- **Model**: PICO 4 (A8110), PICO OS, oversea build
- **Android**: 10 (API 29)
- `DeviceProfile`: detected as `family: pico`

Note: GamePort  v0.9.1, test build ID set as `app.gameport.dev`

## 1. Definitive issues reproduced, cause identified, fixed

Each of these was reproduced on the device, its cause read from the logs, and a fix verified on the device.

### 1.1 When the install approval is missed, it waits indefinitely with no fail/timeout

**Symptom.** Two games stuck on "Installing", there is no cancel or retry so stayed like this for for two days. Restarting GamePort didn't clear it, the resume took the install up again, found the already-patched APK, skipped the download and waited in the same.

**Cause.** `PackageGateway.awaitStatus` suspended on a broadcast. When GamePort lacks the "install unknown apps" app-op, Android still sends `STATUS_PENDING_USER_ACTION`, but the window it returns is the system's own unknown-sources page, which never reports an install status, and the UI had no way to recover (`onCancel = null` for `Patching`/`Installing`/`Finishing`).

**Verified** Device appops log:

```
REQUEST_INSTALL_PACKAGES: allow; rejectTime=+1d19h16m30s ago     (= Oct 27 10:26)
downloads/1579880/patched/LightBrigade.apk                        (written Oct 27 10:25)
downloads/1579880/   —  no .gameport-failed, no .gameport-paused
```

The patched APK was written at 10:25, the app-op was rejected at 10:26, and no failure marker was ever written, because the code never reached a failure path. It simply hung.

**Fix** (`f5ffafa`). Confirmation is awaited for five minutes from the prompt (rather than from the start), so the system still gets as long as it needs for its own work. The installer session is abandoned when given up on. Patching and installing both offer a way out. The permission is checked before a download starts, with the settings page offered on the failure.

**Verified**:

```
08:28:50  install: the system asks for a confirmation, its window is opened (CONFIRM_INSTALL)
08:33:50  install: the confirmation got no answer in 300 s: given up on
08:33:50  failed: NotConfirmed
```

### 1.2 Expansion files could never be placed on Android 10

**Symptom.** Every game installed and then failed with "this game's data could not be placed".

**Cause.** GamePort's only key to shared storage is `MANAGE_EXTERNAL_STORAGE`, which is Android 11+. With nothing else requested, scoped storage left GamePort able to write its own folders only. `hasAllFilesAccess()` always returned `true`, even for versions below Android 11, so GamePort believed it had access and reported a permission problem as an unexplained failure.

**Verified**:

```
the folder /storage/emulated/0/Android/obb/<package> cannot be created
  (exists=false, parent writable=false)
```

**Fix** (`f82a643`). Android 10 is asked for `WRITE_EXTERNAL_STORAGE` with `requestLegacyExternalStorage`, both capped at API 29 and ignored above it, asked for at launch. `hasAllFilesAccess()` now checks the permission below Android 11 and the special access above.

**Verified**:

```
/storage/emulated/0/Android/obb exists=true canWrite=true
creating a folder in it: true     writing a file inside it: ok
...
11:32:36  placed in Android/obb/com.GexagonVR.HardBullet_steam_frame:
          _unitybuiltinassets.bundle, main_assets_all.bundle, … and 14 more
```

26 bundles, 8.5 GB, written directly — no fallback.

### 1.3 "The downloaded APK could not be read" - incorrect message

**Symptom.** Hard Bullet and Richie's Plank both reported a corrupt download. Retrying always failed identically.

**Cause.** The APKs were valid but Android's parser returns null for any APK whose `minSdkVersion` exceeds the device's API level.

**Verified:**

The patched APK, pulled and examined off-device:

```
unzip -t  → No errors detected
871 entries, AndroidManifest.xml present (10,580 bytes)
aapt2 dump badging →
  package: name='com.GexagonVR.HardBullet_steam_frame' versionCode='3015'
  minSdkVersion:'30'        targetSdkVersion:'36'
```

**Fix** (`cf39664`). The manifest is read directly with ARSCLib (patcher already uses for it) and the game's requested version is set to match the device. Checked after the build is chosen and before the patch, so a multi-gigabyte file is not rewritten first.

```
10:53:22  the game asks for Android API 30 and this device runs API 29: it cannot be installed as it is
10:53:22  failed: NeedsNewerAndroid(requiredSdk=30, deviceSdk=29)
```

### 1.4 Auto-resume failed

**Cause.** `resumeInterrupted` queries the library for folder ownership but only takes the first answer. `observeGame` returns `Flow<Game?>` and emits `null` while the library is still loading, and the resume runs five seconds after sign-in, when it usually still is. The game was skipped, and because the line recording a resume comes after the lookup.

**Evidence.** Both leftover games stuck at `Interrupted` with empty event logs across multiple resume triggers.

**Fix** (`13add5f`). The game itself is waited for, within the ten seconds the lookup already allowed, and an empty lookup is recorded.

### 1.5 Cancelling an install deletes the download

**Evidence.**

```
08:27:31  install: given up on, the system is told to forget its session
08:27:32  install requested for The Light Brigade (dlc: [])
08:28:42  download complete      ← 2,551 MB fetched again
```

The cancellation handler deleted the download directory, even though the files were complete and valid and only the confirmation had failed.

**Fix** (`dc321d5`). A cancel while the game is still being fetched still discards it; at the patching or install it keeps it and marks it as user requested stio ti prevent auto resume.

### 1.6 Google's GameActivity is a hard dependency on Android 11

Lowering `minSdkVersion` got Hard Bullet installed, but it crashed at launch:

```
java.lang.NoSuchMethodError: No virtual method setDecorFitsSystemWindows(Z)V in class Landroid/view/Window;
  at androidx.core.view.WindowCompat$Api30Impl.setDecorFitsSystemWindows(WindowCompat.java:168)
  at com.google.androidgamesdk.gametextinput.InputConnection.<init>(InputConnection.java:116)
  at com.google.androidgamesdk.GameActivity$InputEnabledSurfaceView.<init>(GameActivity.java:680)
  at com.unity3d.player.UnityPlayerGameActivity.createSurfaceView(UnityPlayerGameActivity.java:62)
```

`Window.setDecorFitsSystemWindows()` is min API 30. AndroidX's `WindowCompat` has an `Api30Impl` path that calls it without a runtime version check, because the app declared `minSdk 30` so AndroidX skips the check. 

**Fix** (`d6e978e`). The entry point is read from the manifest and the offer to lower is withheld for such a game, which now says there is nothing to lower and that a newer headset would is required. The two classes are named and never matched on the word "GameActivity", because Unreal's activities are called that too (`com.epicgames.unreal.GameActivity`), and run fine on Android 10.

The mechanism above is definitive for this game, needs broader evidence to prove every game using this API would be affected (but overwhelmingly likely).

Evidence:

| Game | Launcher activity | Android 10 |
|---|---|---|
| Space Pirate Trainer | `com.unity3d.player.UnityPlayerActivity` | installs and starts |
| The Light Brigade | `com.unity3d.player.UnityPlayerActivity` | installs and starts |
| Richie's Plank | `com.unity3d.player.UnityPlayerActivity` | installs and starts |
| **Hard Bullet** | **`com.unity3d.player.UnityPlayerGameActivity`** | **installs, crashes at once** |

## 2. Strong evidence, mechanism not fully established

### 2.1 Pico blocks a sideloaded game at launch when it recognises the package and the account does not own it

As per @moi952:

> Pico checks the package name, I think. If it knows it has the same one in the store, then it checks the license on your account. If you don't have it, it blocks you

Install doesn't gey blocked, every game installs with `status 0 (INSTALL_SUCCEEDED)`. Block happens at launch, via `com.bytedance.pico.matrix/.entitlement.ui.OldEntitlementDialogWithGoStore`, which the user sees as **"Verification failed: illegal signature"** with a button to the Pico store. It fires on launch from GamePort's  as well as from the Pico Library, so no launch path works around it.

**Evidence.** Five GamePort-signed builds, same device, same session:

| Game | Package | Frame-specific suffix | Listed on Pico store | Owned on Pico | Entitlement |
|---|---|---|---|---|---|
| Space Pirate Trainer | `com.iillusions.spacepiratetrainer`**`frame`** | yes | yes | no | **passed** |
| Hard Bullet | `com.GexagonVR.HardBullet`**`_steam_frame`** | yes | no | no | **passed** |
| SUPERHOT VR | `com.superhotgame.superhotvr` | no | yes | **yes** | **passed** |
| Richie's Plank | `com.ToastVR.RichiesPlankExperience` | no | yes | no | **blocked** |
| The Light Brigade | `com.FunktronicLabs.TheLightBrigade` | no | **no** | no | **blocked** |
| RUMBLE | `com.BucketheadEntertainment.RUMBLE2` | no | no | no | **passed** |

**Ownership decides a recognised package.** SUPERHOT and Richie's Plank are identical in every measurable respect — GamePort-signed, no platform SDK, Unity with the classic activity, both listed on the Pico store, neither suffixed — and differ only in whether the account owns them on Pico. One launches, one is blocked. So "illegal signature" is a licence failure, and "go to store" is in fact actionable, odd as it is to buy a game on Pico in order to play the Steam build (maybe for portable achievements abd game saves?)

**Problem:** Currently the only explanation for The Light Brigade is a reserved ID for an unpublished build; needs verification from the developer, and if true means there's potentially games that are impossible to verify without actually attempting to install them.

RUMBLE is also the first game found to work on this headset end to end without being owned on Pico: `minSdk 29` so nothing had to be lowered, the classic Unity entry point, no gate, Steam tickets delivered, and it plays. Its controller mapping needs adjusting, which GamePort already says on its page, which is not related to the above.

**A Frame-specific package name is not recognised.** The two suffixed packages passed despite not being owned. Valve's Frame ports do not uniformly rename (seems to be per-developer) so the subset of games that collide is arbitrary from a user's point of view.

**Confirmed by changing the name.** Richie's Plank and SUPERHOT VR were each patched again under a package name of GamePort's making and installed beside the copies already on the device. Neither was gated: zero activity starts of the dialog in either case, compared to copies that were blocked on the same device in the same session with the same lowered `minSdk` and the same patch pipeline. One variable gets rid of the gate so the keying on the package name is causal.

Both renamed builds crashed before any game code ran, which is a fault of the rename and not of the headset: see 4.7.

**What is not established: where recognition comes from.** The Light Brigade is **not listed on the Pico store** and yet is blocked. The developer's own site (`lightbrigadevr.com`) lists Steam, Meta and PS5 but not Pico, and it a store search doesn't find it. This also dismisses an earlier hypothesis that it might be a regional listing.

*Ruled out safely.** No file under `/system/etc`, `/vendor/etc` or `/product/etc` names the package, so recognition is not from a list shipped on the device that a non-rooted device can read. `com.bytedance.pico.matrix` receives `PACKAGE_ADDED` for each install but logs none of its own network calls, so requests and responses are not visible from logcat.

**What can be done safely.** Install another game that is not listed on the Pico store and whose package carries no Frame-specific suffix, and see whether it is blocked too. If several unlisted games are blocked, a single abandoned Pico port stops being a plausible explanation and some broader source of recognition is implied. RUMBLE is the cheapest candidate — 1,335 MB, no expansion files, not listed on the Pico store. Failing that, the developer can simply be asked whether a Pico build ever existed.

### 2.2 A PICO 4 cannot run a growing share of Frame build at all

A game that declares `minSdk 30` is saying it may call methods in Android 11+. Some do and some don't, the number is often whatever is in the engine template. The declaration is what matters to Android's installer.

Where the declaration is conservative, lowering it lets the game install and run and the patch can help for that case. Where the game genuinely calls an Android 11 API, lowering moves the failure from the installer to a crash, as 1.6 shows. We cannot currently tell in advance except for the one case where the entry point itself gives it away.

Valve's Frame builds target modern Android, so the proportion declaring `minSdk ≥ 30` is likely to grow rather than shrink. A PICO 4 **Ultra** (Android 14) takes all of them. On a PICO 4 the realistic framing is a back catalogue — the games whose builds still declare Android 10 — rather than "all your Steam games".

Also relevant: `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` is gated on API 31, so on a PICO 4 **every** install and update asks for confirmation. There is no silent-update path, which makes the device far more exposed to 1.1 than a Quest is.

## 3. Disproven hypotheses

Retained in case useful and to avoid repeating.

| Hypothesis | Why it was held | What killed it |
|---|---|---|
| **The block is at install time** (ours, from a second-hand paraphrase) | The symptom was "the game will not install and run" | Installs return `status 0 (INSTALL_SUCCEEDED)`. The block is at launch. The maintainer's own description never claimed otherwise and was closer to the finding than our reading of it |
| **`is_same_signature:-1` at install shows Pico detecting our signature mismatch** (ours) | It appears on the install of a game that is later blocked, and reads like a verdict | The identical line appears for all seven packages installed during the session, including GamePort itself and including both games that go on to launch. `-1` is a sentinel for *not evaluated*; the field carries no verdict and no discriminating information |
| **The entitlement check lives in the Pico Library UI, not the activity-start path** (ours) | A direct `adb` launch produced no entitlement dialog | That test ran as shell (uid 2000). A GamePort-initiated launch *is* blocked — `08:36:54 check if allow start activity: …OldEntitlementDialogWithGoStore` |
| **Pico matches the installed package against its store catalogue, resolving the store title** (ours) | The app manager showed "The Light Brigade: **Definitive Edition**", read as a catalogue title | The same log line reads `storeName=`, `appId=0`, `storeVersionName=` — no store record resolved. The name is the APK's own label, and GamePort's log calls the game the same thing, because it is the **Steam** title |
| **The Meta Platform SDK (`libovrplatformloader.so`) triggers the check** (ours) | The only blocked game then known carried it; three that passed did not | Richie's Plank carries **no** platform SDK of any kind and is blocked — a direct counter-example, sufficient on its own. Hard Bullet carries none and passes. The loaders correlate with nothing. Not having a replacement explanation does not restore a refuted one |
| **Carrying Pico's own platform SDK exempts a build** (ours) | Space Pirate Trainer and Beat Saber both carry it and both pass | Hard Bullet carries neither and passes |
| **The Light Brigade may simply not be listed in this region** (ours) | It would have explained recognition without a store listing | The developer's own site lists Steam, Meta and PS5 and no Pico. Never shipped to the platform at all |
| **Our patching corrupted the APK** (ours) | "Could not be read", and we rewrite the zip | `unzip -t` clean, `aapt2` reads the package fine, EOCD arithmetic exact. It was `minSdkVersion` |
| **The SUPERHOT test was void** (ours) | It carries no Meta loader, so nothing would check it | Only void under the Meta-loader theory, which is dead. Paired with Richie's Plank it is the decisive ownership comparison |
| **Depot manifests can tell us which platform SDK a game carries** (ours) | `manifestFiles` reads a depot's file list without downloading | A depot manifest lists the depot's files — an APK and expansion bundles. The libraries are *inside* the APK. The audit reported "no platform SDK" for games we had directly observed carrying one (`e0746b6`) |
| **`run-as … mkdir` proves the app can write `Android/obb`** (ours) | It succeeded where the app had failed | A shell forked from adbd gets a different storage view than the app process. The app still could not write until the real fix |
| **Beat Saber is a useful sideloaded control** (ours) | Sideloaded, not owned on Pico, launches fine | It carries *both* platform SDKs, so it is a Pico-aware build, and its provenance is unknown. Meta owns Beat Games, so Pico can never carry it |
| **Unity keeps assets in the APK, Unreal keeps them outside** (ours) | Fitted the APK-size spread | Hard Bullet is Unity with 8.5 GB of asset bundles outside the APK. The per-game figures stand; the engine explanation does not |

## 4. Pending questions

1. **Space Pirate Trainer crashes at startup in the GPU driver.** It passes entitlement and then dies: `SIGSEGV, null pointer dereference, fault addr 0x1c` in `/vendor/lib64/hw/vulkan.kona.so`, called from `libunity.so` on a render thread. Not entitlement, not storage, not install. The obvious reading is a driver-generation mismatch — Frame is a much newer SoC than an XR2 — but **`libXrApiLayer_gameport.so` is loaded in that process and sits in the OpenXR path**, so GamePort's own layer is a live suspect even though it does not appear in the backtrace. A null `VkDevice` returned through the OpenXR graphics binding would produce this signature. Isolating test: re-patch without the XR layer. `repatchWith` exists in the repository but has no UI caller, so this needs tooling.

2. **Why Pico recognises The Light Brigade's package in particular.** Not the current storefront (RUMBLE is absent from it too and passes), not a readable list shipped on the device, and not visible in anything the entitlement service logs. RUMBLE narrows this from "what is the rule" to "what is special about that one name", and the reserved-bundle-ID hypothesis in 2.1 is the only candidate left. Its obvious test should not be run, for the reasons given there; what remains is to ask the developer, or to find a second game that is refused and look for what it shares with The Light Brigade.

3. **Whether lowering `minSdkVersion` ever yields a working game.** It reliably lets one install. Hard Bullet then crashed for a real API-30 dependency; Richie's Plank is blocked by entitlement before we can see. A game with `minSdk ≥ 30`, the classic Unity activity, and either a Frame-suffixed package or Pico ownership would settle it. None of the remaining candidates is known to fit without downloading it.

4. **Why Pico classifies our builds `other,2d_app`** while a sideloaded Beat Saber is `other,sdk_app`, and whether that affects anything. Noted, not pursued.

5. **`PicoPlatform.immersiveLauncherCategories = listOf("com.yvr.intent.category.VR")`** names YVR, a different manufacturer. Patched games get every platform's categories so they still carry Pico-recognised ones, but `PackageGateway.launchIntent` uses only the detected platform's list, so on Pico GamePort launches games with the YVR category alone. No failure has been traced to this. A lead, not a finding.

6. **Crazy Kung Fu's depot manifests cannot be read at all** — one depot, no answer, cause unknown.

7. **An install given up on leaves a dead confirmation on screen.** Abandoning the system's session does not dismiss the window the system has already drawn, so a player who cancels an install, or whose confirmation times out, can be left facing a prompt that looks live and does nothing when accepted: the session behind it is gone. Hit twice while testing, each time costing a five-minute window, and a player who cancels and retries would hit it the same way — so it is a consequence of the cancel button added in `f5ffafa` rather than anything of the headset's.

   Android offers no way out of it directly. The confirmation is started with `startActivity` from a service context rather than for a result, and it runs in `com.android.packageinstaller`'s own task, so there is no handle on which to finish it. What GamePort could do is bring its own window forward once it gives up, which would put the failure and its retry button in front of the player instead of a dialog that cannot work. Whether to take the focus like that on a headset is a judgement for the maintainer, so it is written down rather than done.

   What does behave correctly underneath it: the wait ends at 300 seconds to the moment, the outcome is recorded as `NotConfirmed` rather than hanging, and every expansion file set aside for the update is put back — three times over the course of one afternoon on a 2.6 GB file, without loss.

8. **The rename lifts the gate and breaks the game.** Both renamed builds died identically before any game code ran:

   ```
   java.lang.RuntimeException: Unable to start activity ComponentInfo{…/com.unity3d.player.UnityPlayerActivity}:
     android.content.res.Resources$NotFoundException: String resource ID #0x0
     at android.app.ActivityThread.performLaunchActivity
   ```

   The cause is the implementation, not the idea. `androidManifest.setPackageName` rewrites only the manifest, so `resources.arsc` goes on declaring the original package and Android cannot resolve a string resource while creating the activity. Proven by SUPERHOT VR, which launched perfectly in its original form earlier the same day — Steam ticket delivered, 13 achievements synced — and whose renamed build differs in nothing else.

   The manifest transformation itself is clean: applying the engine's rename and its JSON round-trip to a patched APK off-device and diffing the result against the same round-trip without the rename gives exactly one differing line, the package string. So nothing is lost or reordered in the manifest; the fault is the divergence it creates with the resource table.

   Doing it properly means `ZipRewriter` replacing `resources.arsc` as well, against an engine whose design is that only the manifest is decoded and every other entry copied through untouched. Not attempted: it is real work for a feature nobody has decided to ship, and the question it was built to answer is already answered.

## 5. Proposals

- **Add `minSdk` to `android-catalog.json`.** It already holds `android`, `installable`, `frame`, `deck`. A `minSdk` field would let the app warn before a download instead of after. The same applies to whatever signal ends up predicting the entitlement block, once 2.1's open question is answered.
- **Separate a PICO 4 from a PICO 4 Ultra in the compatibility table.** `Build.MODEL` and `Build.DEVICE` are already collected and sent with a vote, so nothing more needs gathering; it is the presentation that buckets every Pico into one `pico` column. One of those devices is Android 10 and the other Android 14, and 2.2 is the difference between a game being installable and not, so a PICO 4 owner currently reads an Ultra's verdict as if it were their own. Splitting the column would make the table mean what players take it to mean — and the reverse mistake is easy to make while testing, too.
- **Fetch the APK before the expansion files.** `GameDownloader.download` takes depot IDs, and the APK shares a depot with the obb files, so there is no way to validate compatibility before committing to the whole download. The saving is large and uneven:

  | Game | APK | Beside it | APK share |
  |---|---|---|---|
  | Metro Awakening | 49 MB | 19,107 MB | **0.3 %** |
  | Moss II VR | 69 MB | 5,087 MB | 1.3 % |
  | Hard Bullet | 575 MB | 9,132 MB | 6 % |
  | The Light Brigade | 255 MB | 2,296 MB | 10 % |
  | Richie's Plank | 1,050 MB | 0 | 100 % |

## Tooling added

A debug-only broadcast receiver (`app/src/debug/`, `scripts/install_debug.sh`) — a test build only, absent from release, which `./gradlew :app:processReleaseMainManifest` can be checked against. Commands: `state`, `perm`, `storage` (tries the write inside the app process), `games`, `audit`, `log`, `files`, `install`, `cancel`, `discard`. See `DEVELOPMENT.md`.

A report of a failed install is now saved to `Download/` the moment it fails (`ae68bd9`), because what explains a failure is cleared or written over soon after, and the failure on screen names the file.
