# Installing retro-style

This guide adds retro-style to a LineageOS 23.2 source tree. Each step says what to do, why, and
how to check it worked. Paths are relative to the root of your source tree (the directory with
`build/`, `frameworks/` and `vendor/`).

You touch four places:

| Where | What | Step |
|---|---|---|
| `vendor/retro-style/`, `vendor/dual-shade/` | this repo and the Dual Shade repo it needs | 1 |
| `frameworks/base`, `packages/apps/Launcher3` | patches from both repos (SystemUI and the taskbar) | 2 |
| your device makefile | one `inherit-product` line | 3 |
| nothing else | the build picks up the overlays, the app and the flags from the makefile | 4 |

## 1. Get the repo

The repo must live at `vendor/retro-style`: `retro_style.mk` and the release config map refer to
that path.

**Option A: clone it.**

```sh
git clone https://github.com/Bliss-Bass/retro-style vendor/retro-style
```

**Option B: let `repo` manage it** (recommended if you sync often). Create
`.repo/local_manifests/retro-style.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <remote name="bliss-bass" fetch="https://github.com/Bliss-Bass" />
  <project path="vendor/retro-style" name="retro-style" remote="bliss-bass" revision="main" />
  <project path="vendor/dual-shade" name="dual-shade" remote="bliss-bass" revision="main" />
</manifest>
```

then run `repo sync vendor/retro-style vendor/dual-shade`.

**Also get vendor/dual-shade.** retro-style's panels are SystemUI's Dual Shade, which
[dual-shade](https://github.com/Bliss-Bass/dual-shade) switches on and fixes (build flags, an
overlay, five SystemUI patches). The manifest above already includes it; with option A, clone it
too: `git clone https://github.com/Bliss-Bass/dual-shade vendor/dual-shade`. `retro_style.mk`
inherits it and stops the build with a clear message if it's missing.

**Check:** `ls vendor/retro-style/retro_style.mk vendor/dual-shade/dual_shade.mk` prints both
files.

## 2. Apply the patches

Most of retro-style is additive (an app, overlays, flag values), but two projects need code
changes:

- `frameworks/base` (SystemUI): panels that open from the tray, the KitKat Quick Settings skin, the
  status icons the tray shows, and the pack colors. dual-shade adds its own SystemUI series: the
  fixes Dual Shade needs on large screens.
- `packages/apps/Launcher3` (the taskbar): the tablet system bar layout and its tray.

[PATCHES.md](PATCHES.md) explains every patch.

Start a topic branch in both projects so `repo sync` keeps your commits apart from upstream's:

```sh
repo start retro-style frameworks/base packages/apps/Launcher3
```

See which patches are missing, try them in a throwaway worktree, then apply:

```sh
vendor/dual-shade/tools/apply-patches.sh check && vendor/dual-shade/tools/apply-patches.sh apply
vendor/retro-style/tools/apply-patches.sh status
vendor/retro-style/tools/apply-patches.sh check
vendor/retro-style/tools/apply-patches.sh apply
```

`apply` runs `git am --3way`, so each patch becomes a real commit with its original message. If a
patch conflicts (upstream changed the same lines), git stops: fix the files, `git add` them, run
`git am --continue`, then run `apply` again for the rest.

**Check:** `vendor/retro-style/tools/apply-patches.sh status` lists every patch as `[applied]`.

**After a later `repo sync`:** your commits stay on the `retro-style` branch. If `repo sync`
rebased them away (for example after `repo sync -d`), run `apply` again; it only applies what's
missing.

## 3. Add it to your device

Find the makefile that defines your product. In LineageOS that's usually
`device/<vendor>/<device>/lineage_<device>.mk` (it `inherit-product`s the device and common
makefiles). Add, near the other `inherit-product` lines:

```make
# retro-style: theme packs and the tablet system bar (vendor/retro-style)
RETRO_STYLE_TYPE := kitkat        # optional default pack: any id in themes/ (default: material)
RETRO_STYLE_LAYOUT := system_bar  # optional: stock | system_bar (default: what the pack suggests)
RETRO_STYLE_THEMES := material m3 kitkat jellybean   # optional: packs to ship (default: all)
RETRO_STYLE_BLACK := on           # optional: pure black dark mode for every pack (on | off)
RETRO_STYLE_BAR_BACKGROUND := gradient  # optional: system bar on the home screen
                                        # (stock | solid | gradient | translucent | transparent)
$(call inherit-product, vendor/retro-style/retro_style.mk)
```

`RETRO_STYLE_TYPE`, `RETRO_STYLE_LAYOUT`, `RETRO_STYLE_BLACK` and `RETRO_STYLE_BAR_BACKGROUND` only
set what a fresh install starts with; users change all of them in Settings. `RETRO_STYLE_THEMES` decides which packs are in the image at all. Set
them **before** the `inherit-product` line, because the makefile reads them as it's included.
`tools/theme_tool.py list` shows every pack id.

What `retro_style.mk` adds to your product:

- `ro.retro.ui_style.default` and `ro.retro.ui_layout.default` in `/system_ext/build.prop`, plus
  `ro.retro.ui_black.default` and `ro.retro.ui_bar_background.default` when those variables are set
- the `RetroStyle` app (`/system_ext/priv-app/RetroStyle`) and its permission allowlist
- one `RetroTheme_<id>` module per pack: its `theme.json` in
  `/product/etc/retro-style/themes/` and its overlays in `/product/overlay`
  (`themes/<id>/Android.bp`)
- the system bar overlay in `/product/overlay` (`overlays/*/Android.bp`)
- everything `vendor/dual-shade/dual_shade.mk` adds: the Dual Shade overlay and the release config
  map that sets two SystemUI aconfig flags (see dual-shade's README)

**Check:** `get_build_var PRODUCT_PACKAGES | tr ' ' '\n' | grep -i retro` (after `breakfast`) lists
`RetroStyle`, the `RetroTheme_*` packs and the layout overlays.

### If your tree doesn't build the bp4a release

The Dual Shade flag values are attached to the `bp4a` release in vendor/dual-shade; its README
explains how to add them to another release.

## 4. Build

Build the way you always do, for example:

```sh
source build/envsetup.sh
breakfast <device>
m bacon
```

## 5. Check it on the device

After flashing and finishing setup:

1. **Settings > System > UI style** is there and lists the packs you ship. Pick **KitKat**: the
   screen restyles, SystemUI restarts once, and your open windows come back.
2. The style was applied:
   ```sh
   adb shell settings get secure retro_ui_applied
   # style=kitkat;theme=kitkat;variant=kitkat_stock;layout=system_bar;anchor=bottom_end;...
   adb shell cmd overlay list | grep retro
   # [x] org.retro.theme.kitkat.android  ... (enabled overlays have [x])
   adb shell ls /product/etc/retro-style/themes
   # one <id>.json per pack
   ```
3. Dual Shade is on: Notifications and Quick Settings open as separate floating panels.

For a full check, run the on-device matrix ([TESTING.md](TESTING.md)).

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `apply-patches.sh check` fails | Upstream moved code the patch touches. Apply by hand with `git am --3way`, resolve the conflict, and regenerate the patch ([PATCHES.md](PATCHES.md#updating-a-patch)). |
| Build error: `RETRO_STYLE_TYPE must be ...` or `RETRO_STYLE_THEMES has packs that aren't in ...` | A typo, or a default pack you don't ship; the allowed ids are in the message. |
| A pack is missing from Settings | It isn't in `RETRO_STYLE_THEMES`, or its `Android.bp` is stale: run `tools/theme_tool.py check`. |
| Build error: `retro-style needs vendor/dual-shade` | Get dual-shade too (step 1). |
| Build error mentioning `release_config_map` or `aconfig_value_set-dual-shade-bp4a` | dual-shade isn't at `vendor/dual-shade`, or your release isn't `bp4a` (see its README). |
| Boot stops with a privapp-permissions error in the log | `privapp-permissions-org.retro.style.xml` didn't install; check `RetroStyle` is in `PRODUCT_PACKAGES` (its `required` pulls the file in). |
| UI style page missing from Settings | `RetroStyle` isn't installed, or it isn't signed with the platform key (it shares the system user ID, so Android refuses any other signature). |
| Choosing a style changes colors but not SystemUI or the taskbar | The patches aren't applied, or SystemUI/Launcher3 weren't rebuilt after applying them. |
| Panels are single full-width shades, not floating panels | dual-shade's patches or makefile aren't in the build, or the user picked the combined shade on the Shade panels page in Settings (`adb shell settings put secure dual_shade 1` switches back). |

## Removing it

Remove the `inherit-product` line, drop the `retro-style` topic branches
(`repo abandon retro-style frameworks/base packages/apps/Launcher3`), delete
`vendor/retro-style`, and rebuild.
