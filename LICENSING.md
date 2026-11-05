# Licensing

retro-style is dual-licensed by Navotpala Tech (Bliss Co-Labs Inc.):

- **GNU Affero General Public License v3.0** (`LICENSE`). Free for everyone who meets its terms,
  which is the normal case for open-source ROMs and projects: if you distribute retro-style, or a
  build that contains it, you provide the complete corresponding source code under the same
  license.
- **Commercial license** (`LICENSE_COMMERCIAL`). For companies that want to ship retro-style
  without the AGPL's obligations, for example in devices or distributions whose source they don't
  publish. Contact info@navotpala.tech.

SPDX: `AGPL-3.0-only OR LicenseRef-Navotpala-Commercial`.

## What the dual license covers

Everything in this repo that Navotpala Tech wrote, unless listed below: the RetroStyle app,
the overlays' resources and manifests, the build files, the tools, the tests, the docs, the new
files the patches add, and the changes the patches make.

## What stays under the Apache License 2.0

These come from the Android Open Source Project (see `NOTICE`) and keep their license:

| What | Where |
|---|---|
| Android 4.1 and 4.4 artwork and fonts, copied unmodified | `assets/aosp/`, and the PNGs in the theme packs (`themes/<id>/`); `assets/ASSET_SOURCES.md` and each pack's `SOURCES.md` list every file's origin |
| The AOSP files the patches change | `frameworks/base` and `packages/apps/Launcher3`: each changed file keeps its Apache header; the changes themselves are under the dual license |
| Material Design icon path data | the `ic_retro_*` drawables the Launcher3 patches add (noted in each file) |

## Building it into a ROM

A ROM that includes retro-style under the AGPL must offer the complete corresponding source of
what it ships: retro-style itself plus the patched `frameworks/base` (SystemUI) and Launcher3,
since the patches add AGPL code to them. Publishing the ROM's source trees (as open-source ROMs
do) satisfies this.

## Contributing

retro-style can only stay dual-licensed if Navotpala Tech may offer every part under both
licenses. By submitting a contribution you agree that it may be distributed under the GNU Affero
General Public License v3.0 and under Navotpala Tech's commercial license.
