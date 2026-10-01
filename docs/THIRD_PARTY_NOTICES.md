# Third-party notices

GamePort is open source. Everything below is used under its own licence; keep this file up to date whenever code is copied or a dependency is added.

## Forks (our own repositories, upstream history kept)

| Project | Used in | Licence | Notes |
|---|---|---|---|
| [ovrport](https://github.com/ovrport/app) | `core/patch` (two files adapted: `VrLauncherPatch.kt`, `JsonNav.kt`) | GPLv3 | Idea and code of the launcher patch, and the helpers walking the manifest. No fork is kept. |
| [Goldberg Steam Emulator](https://gitlab.com/Mr_Goldberg/goldberg_emulator) | repository `gameport-steamworks-shim` | LGPLv3 | Base of the Steamworks shim. Our changes are on branch `gameport`. |

## Code copied from other projects

| Project | Licence | What we copy |
|---|---|---|
| [GameNative](https://github.com/utkarshdalal/GameNative) (kept outside this repository) | GPLv3 | Nothing yet. Files copied later are listed here with their path and origin. |

## Build-time libraries

| Library | Licence | Used for |
|---|---|---|
| protobuf (v35.1) | BSD-3-Clause | Linked statically into the shim. |
| Abseil | Apache-2.0 | Linked statically into the shim (protobuf dependency). |
| utf8_range | MIT | Linked statically into the shim (protobuf dependency). |
| ARSCLib | to be confirmed | Binary manifest/resource editing in the patcher (through ovrport). |

## Not redistributed

- **Valve Steamworks SDK headers.** `gameport-steamworks-shim/sdk_includes/isteamclient023.h` is generated locally from an SDK supplied by the developer (`tools/generate_isteamclient023.py`) and is git-ignored. Valve's SDK terms may not allow republishing its headers; decide how the open-source build obtains them before publishing.

## OpenXR headers (used to build GamePort's OpenXR layer)
- Khronos OpenXR-SDK, `include/openxr/*.h` — Apache-2.0 (and MIT). Fetched at build time by
  `scripts/build_xr_layer.sh` into a cache folder; not stored in this repository.
