# Installing retro-style

This guide adds retro-style to a LineageOS 23.2 source tree. Each step says what to do, why, and
how to check it worked. Paths are relative to the root of your source tree (the directory with
`build/`, `frameworks/` and `vendor/`).

You touch four places:

| Where | What | Step |
|---|---|---|
| `vendor/retro-style/` | this repo | 1 |
| `frameworks/base`, `packages/apps/Launcher3` | patches (SystemUI and the taskbar) | 2 |
| your device makefile | one `inherit-product` line | 3 |
| nothing else | the build picks up the overlays, the app and the flags from the makefile | 4 |

## 1. Get the repo

The repo must live at `vendor/retro-style`: `retro_style.mk` and the release config map refer to
that path.

**Option A: clone it.**

```sh
git clone <retro-style repo URL> vendor/retro-style
```

**Option B: let `repo` manage it** (recommended if you sync often). Create
`.repo/local_manifests/retro-style.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <!-- Replace fetch and name with wherever you host retro-style. -->
  <remote name="retro-style" fetch="https://github.com/<owner>" />
  <project path="vendor/retro-style" name="retro-style" remote="retro-style" revision="main" />
</manifest>
```

then run `repo sync vendor/retro-style`.

**Check:** `ls vendor/retro-style/retro_style.mk` prints the file.

## 2. Apply the patches

Most of retro-style is additive (an app, overlays, flag values), but two projects need code
changes:

- `frameworks/base` (SystemUI): panels that open from the tray, the KitKat Quick Settings skin, the
  status icons the tray shows, and four fixes Dual Shade needs on large screens.
- `packages/apps/Launcher3` (the taskbar): the tablet system bar layout and its tray.

[PATCHES.md](PATCHES.md) explains every patch.

Start a topic branch in both projects so `repo sync` keeps your commits apart from upstream's:

```sh
repo start retro-style frameworks/base packages/apps/Launcher3
```

See which patches are missing, try them in a throwaway worktree, then apply:

```sh
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
# retro-style: retro UI styles and the tablet system bar (vendor/retro-style)
RETRO_STYLE_TYPE := kitkat        # optional: materialyou (default) | m3 | kitkat
RETRO_STYLE_LAYOUT := system_bar  # optional: stock | system_bar (default: system_bar for kitkat)
$(call inherit-product, vendor/retro-style/retro_style.mk)
```

The two variables only set what a fresh install starts with; users change both in Settings. Set
them **before** the `inherit-product` line, because the makefile reads them as it's included.

What `retro_style.mk` adds to your product:

- `ro.retro.ui_style.default` and `ro.retro.ui_layout.default` in `/system_ext/build.prop`
- the `RetroStyle` app (`/system_ext/priv-app/RetroStyle`) and its permission allowlist
- nine overlays in `/product/overlay` (`overlays/*/Android.bp`)
- `PRODUCT_RELEASE_CONFIG_MAPS += vendor/retro-style/config/aconfig/release_config_map.textproto`,
  which sets two SystemUI aconfig flags for Dual Shade (see
  [HOW_IT_WORKS.md](HOW_IT_WORKS.md#dual-shade-and-aconfig))

**Check:** `get_build_var PRODUCT_PACKAGES | tr ' ' '\n' | grep -i retro` (after `breakfast`) lists
`RetroStyle` and the overlays.

### If your tree doesn't build the bp4a release

LineageOS 23.2 builds release `bp4a` (`breakfast` reads it from
`vendor/lineage/vars/aosp_target_release`). The flag values are attached to that release in
`config/aconfig/release_configs/bp4a.textproto`. If you lunch a different release, copy that file
to `release_configs/<release>.textproto` and change its `name`.

## 4. Build

Build the way you always do, for example:

```sh
source build/envsetup.sh
breakfast <device>
m bacon
```

## 5. Check it on the device

After flashing and finishing setup:

1. **Settings > System > UI style** is there. Pick **KitKat**: the screen restyles, SystemUI
   restarts once, and your open windows come back.
2. The style was applied:
   ```sh
   adb shell settings get secure retro_ui_applied
   # style=kitkat;variant=kitkat_stock;layout=system_bar;anchor=bottom_end;...
   adb shell cmd overlay list | grep retro
   # [x] org.retro.style.kitkat.android  ... (enabled overlays have [x])
   ```
3. Dual Shade is on: Notifications and Quick Settings open as separate floating panels.

For a full check, run the on-device matrix ([TESTING.md](TESTING.md)).

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `apply-patches.sh check` fails | Upstream moved code the patch touches. Apply by hand with `git am --3way`, resolve the conflict, and regenerate the patch ([PATCHES.md](PATCHES.md#updating-a-patch)). |
| Build error: `RETRO_STYLE_TYPE must be ...` | A typo in the variable; allowed values are in the message. |
| Build error mentioning `release_config_map` or `aconfig_value_set-retro-style-bp4a` | The repo isn't at `vendor/retro-style`, or your release isn't `bp4a` (see step 3). |
| Boot stops with a privapp-permissions error in the log | `privapp-permissions-org.retro.style.xml` didn't install; check `RetroStyle` is in `PRODUCT_PACKAGES` (its `required` pulls the file in). |
| UI style page missing from Settings | `RetroStyle` isn't installed, or it isn't signed with the platform key (it shares the system user ID, so Android refuses any other signature). |
| Choosing a style changes colors but not SystemUI or the taskbar | The patches aren't applied, or SystemUI/Launcher3 weren't rebuilt after applying them. |
| Panels are single full-width shades, not floating panels | The aconfig map isn't in the build (step 3), or the secure setting `dual_shade` is 0 (RetroStyle sets it to 1 at first boot; `adb shell settings put secure dual_shade 1` and reboot). |

## Removing it

Remove the `inherit-product` line, drop the `retro-style` topic branches
(`repo abandon retro-style frameworks/base packages/apps/Launcher3`), delete
`vendor/retro-style`, and rebuild.
