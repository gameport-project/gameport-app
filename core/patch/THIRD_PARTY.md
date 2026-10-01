# Third-party code in `:core:patch`

- `json/JsonNav.kt` and `patches/VrLauncherPatch.kt` are adapted from ovrport
  (https://github.com/) by crx, GPLv3: the JSON navigation helpers and the launcher-entry patch logic.
  GamePort is GPLv3 as well.
- ARSCLib (Apache-2.0) edits the binary AndroidManifest; apksig (Apache-2.0) signs the APK;
  Bouncy Castle (MIT) creates the self-signed signing certificate.
