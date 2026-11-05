# Testing retro-style

From quickest to most thorough.

## Without a device

| Check | Command | Catches |
|---|---|---|
| Patches apply | `tools/apply-patches.sh check` | Upstream changes that break a patch |
| The app compiles | `cd RetroStyle && ./gradlew :app:assembleRelease` | Kotlin and resource errors (needs an Android SDK; set `sdk.dir` in `RetroStyle/local.properties`) |
| Theme packs | `tools/theme_tool.py check` (add `--apks out/target/product/<device>` after a build) | Bad `theme.json` fields, stale generated files, resource names the target lacks, low panel contrast; with `--apks`, overlays that don't link |
| A layout overlay compiles | `prebuilts/sdk/tools/linux/bin/aapt2 compile --dir vendor/retro-style/overlays/<Name>/res -o /tmp/out` | Resource syntax errors |
| The makefile | `breakfast <device>` then `get_build_var PRODUCT_PACKAGES` | Bad `RETRO_STYLE_*` values, missing packages |

## On a device, by hand

1. **Settings > System > UI style** opens, themed after the current pack, with the packs grouped
   under Today's Android, Android through the years, and ROM and community classics.
2. Switch between packs. Each switch applies right away; switching to or from a pack with a
   different shade skin or panel colors restarts SystemUI once and your desktop windows come
   back at the same place. Packs that force dark or light mode switch it, and Material You gives
   your own setting back.
3. **Bottom bar > Tablet system bar:** back/home/recents on the left, the tray on the right. Turn
   on the extra buttons and the apps, and try the three app positions.
4. Tap the tray's bell and its clock: Notifications and Quick Settings open above the tray. The
   whole bar stays in front of the panel's blur.
5. Lock the screen (with a lockscreen set) and unlock: no SystemUI crash.
6. Post a notification (`adb shell cmd notification post -S bigtext -t title tag text`): it shows
   as a heads-up and goes away after a few seconds, and taps elsewhere keep working meanwhile.

Useful commands:

```sh
adb shell settings get secure retro_ui_applied       # the style SystemUI and the taskbar use
adb shell cmd overlay list | grep retro               # [x] = enabled
adb shell settings get secure theme_customization_overlay_packages
adb logcat -s RetroStyle                              # what the app applied and restarted
```

Apply settings from adb (what the page does):

```sh
adb shell settings put secure retro_ui_style kitkat
adb shell am broadcast -a org.retro.style.action.APPLY -n org.retro.style/.ApplyReceiver
```

## The matrix

`tests/retro_style_matrix.sh` runs on the device as root and applies about 50 combinations of
the options, then (in `full` mode) every installed pack. For each one it checks:

- `retro_ui_applied` holds the expected pack and skin and the other values
- only the chosen pack's overlays and the right status bar overlay are on, and the theme JSON has
  the pack's seed color
- SystemUI and the launcher are running and stay up while both panels open and close
- no crash and no ANR was logged
- an open Settings window survived the restarts

It ends with four style switches in a row without waiting, then restores your settings.

```sh
adb root
adb push vendor/retro-style/tests/retro_style_matrix.sh /data/local/tmp/
adb shell sh /data/local/tmp/retro_style_matrix.sh quick    # or: full (about 20 minutes)
adb pull /data/local/tmp/retro_matrix/
```

`retro_matrix/results.tsv` has one row per combination; for failures there are crash logs,
logcat and screenshots named after the combination number.
