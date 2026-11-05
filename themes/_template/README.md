# Theme pack template

This folder is the starting point for a new pack. It isn't built or installed (folders that start
with `_` are skipped). The full rules are in [docs/THEME_SPEC.md](../../docs/THEME_SPEC.md); this
page is the short version.

## Make a pack in five steps

1. Copy the template under a new id (lowercase letters, digits and `_`):

   ```sh
   tools/theme_tool.py new my_theme
   ```

   That creates `themes/my_theme/` with this `theme.json` (its id already set). This README stays
   behind.

2. Edit `themes/my_theme/theme.json`. Delete the fields you don't need: only `schema`, `id`,
   `name`, `summary`, `category`, `authors` and `palette` are required. An editor that knows JSON
   Schema (VS Code, Android Studio) checks the file as you type through its `$schema` line.

3. Optional: add hand-made resources, one folder per app you change:

   | Folder | Overlays | Use it for |
   |---|---|---|
   | `android/res/` | the framework | colors, sizes, drawables every app sees |
   | `systemui/res/` | SystemUI | shade, notifications, volume, power menu |
   | `settings/res/` | Settings | Settings-only sizes and colors |
   | `launcher3/res/` | Launcher3 | taskbar, system bar tray icons |
   | `icons_android/res/` | the framework | Wi-Fi, Bluetooth and other system icons |
   | `icons_systemui/res/` | SystemUI | navigation and status bar icons |
   | `icons_settings/res/` | Settings | Settings icons |
   | `icons_launcher/res/` | Launcher3 | launcher icons |

   Each one holds normal Android resources (`values/`, `drawable/`, `drawable-xhdpi/`...) with the
   same names as the ones they replace. Don't repeat what `theme.json` already sets (corners,
   widgets, fonts, bar color): the tool makes those for you.

4. Generate the build files and check the pack:

   ```sh
   tools/theme_tool.py gen my_theme
   tools/theme_tool.py check my_theme
   # with a built tree, also link every overlay like the build does:
   tools/theme_tool.py check my_theme --apks out/target/product/<device>
   ```

   `gen` writes `Android.bp`, the overlay manifests and `res_gen/` folders, and adds the pack to
   `themes/themes.mk`. Commit them with your `theme.json`.

5. If you copied images or fonts from AOSP, import them with `assets/import_aosp_assets.sh` first,
   then run `tools/theme_tool.py sources my_theme` to write the pack's `SOURCES.md`. Artwork you
   made yourself needs a `SOURCES.md` you write by hand that says so. Never copy from themes that
   don't grant a license.

## The fields in theme.json

- `palette`: `"source": "wallpaper"` keeps the user's Material You colors. `"preset"` derives every
  system color from `seed` with Monet; `style` picks how (`TONAL_SPOT` is calm, `VIBRANT` and
  `EXPRESSIVE` are louder, `MONOCHROMATIC` is grey).
- `night`: `dark` or `light` forces the mode while the pack is on; `system` leaves it alone.
- `black`: pure black backgrounds in dark mode (LineageOS only).
- `font`: system font families for body and headline text.
- `widgets`: `holo`, `classic` or `material` swap the framework widgets for an older family;
  `default` keeps today's.
- `shape.corner`: one radius for dialogs, buttons, cards, notifications and the shade.
- `shell`: SystemUI and system bar looks from the retro-style patches: `skin` (`stock` or the
  `kitkat` tablet panels), `panel` and `bar` background colors, the kitkat skin's `accent`, the
  `layout` suggested for builds, the `bar_apps` default and the home screen bar background
  (`bar_home`).
