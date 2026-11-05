# Theme pack spec

This is the rulebook for designing a retro-style theme pack. A pack is one folder,
`themes/<id>/`, and the OS picks it up without code changes: the build installs it, and
**Settings > System > UI style** lists it. The 18 packs in `themes/` follow these rules and are the
best examples. `themes/_template/` is the starting point for a new one.

If you only want the steps, read [themes/_template/README.md](../themes/_template/README.md).
This page explains the why, so a pack looks right everywhere and keeps working.

## What a pack can change

Android draws its UI from several layers, and each one is reached a different way. A pack
describes what it wants in each layer; the RetroStyle app and SystemUI apply it.

| Layer | What it changes | How the pack sets it | How it reaches the OS |
|---|---|---|---|
| 1. Palette | Every system color, in every app that uses system colors | `palette` | Theme JSON: SystemUI's Monet builds the palettes |
| 2. Light and dark | Dark mode on, off or the user's choice; pure black | `night`, `black` | RetroStyle sets the UI mode and LineageOS's black theme |
| 3. Typography | Body and headline fonts | `font` | A generated font overlay, applied from the theme JSON |
| 4. Shape | Corner radius of dialogs, buttons, cards, notifications, Quick Settings, the shade | `shape` | Generated size overlays for framework-res, SystemUI and Settings |
| 5. Widgets | Buttons, checkboxes, switches, text fields, progress bars, lists | `widgets` | A generated framework overlay that re-parents the widget styles |
| 6. Icons | Navigation, status and system icons | `icons_*/res/` folders | Icon pack overlays, applied from the theme JSON |
| 7. Shell | Shade skin and colors, system bar color, bar defaults | `shell` | `retro_ui_applied` (read by the patched SystemUI and Launcher3) and a generated Launcher3 overlay |
| 8. Anything else | Any resource of framework-res, SystemUI, Settings or Launcher3 | `android/res/`, `systemui/res/`, ... | Hand-made overlays |

Layers 1 to 7 are tokens: a few values in `theme.json` that `tools/theme_tool.py` turns into the
right resources for every app. Prefer them. Layer 8 is for the details tokens can't express, and
it's where packs break when Android updates, so keep it small.

## The pack folder

```
themes/<id>/
  theme.json                 the pack (you write this)
  SOURCES.md                 where every image and font comes from (needed when there are any)
  <target>/res/              hand-made resources for one target (optional)
  <target>/res_gen/          made by theme_tool.py from theme.json (don't edit)
  <target>/AndroidManifest.xml   made by theme_tool.py (don't edit)
  Android.bp                 made by theme_tool.py (don't edit)
```

`<target>` is one of:

| Target | Overlays | Overlay category | Switched by |
|---|---|---|---|
| `android` | framework-res | `org.retro.theme` | RetroStyle |
| `systemui` | SystemUI | `org.retro.theme` | RetroStyle |
| `settings` | Settings | `org.retro.theme` | RetroStyle |
| `launcher3` | Launcher3 | `org.retro.theme` | RetroStyle |
| `font` | framework-res | `android.theme.customization.font` | SystemUI, from the theme JSON |
| `icons_android` | framework-res | `android.theme.customization.icon_pack.android` | SystemUI, from the theme JSON |
| `icons_systemui` | SystemUI | `android.theme.customization.icon_pack.systemui` | SystemUI, from the theme JSON |
| `icons_settings` | Settings | `android.theme.customization.icon_pack.settings` | SystemUI, from the theme JSON |
| `icons_launcher` | Launcher3 | `android.theme.customization.icon_pack.launcher` | SystemUI, from the theme JSON |

Each target becomes one overlay APK named `org.retro.theme.<id>.<target>` (with `_` turned into
`.`), for example `org.retro.theme.kitkat.icons.systemui`. The `android.theme.customization`
categories are the same ones the stock Wallpaper & style picker uses, so a pack's font and icons
behave exactly like ThemePicker's.

## theme.json, field by field

```json
{
  "$schema": "../theme.schema.json",
  "schema": 1,
  "id": "kitkat",
  "name": "KitKat",
  "summary": "Android 4.4 (2013): black and grey Holo Dark with Holo blue accents, Roboto, square corners and the tablet system bar.",
  "category": "android",
  "order": 44,
  "authors": ["Navotpala Tech"],
  "palette": { "source": "preset", "seed": "#33B5E5", "style": "VIBRANT" },
  "night": "dark",
  "black": true,
  "font": { "body": "sans-serif", "body_medium": "sans-serif-medium",
            "headline": "sans-serif", "headline_medium": "sans-serif-medium" },
  "widgets": "holo",
  "shape": { "corner": "2dp", "progress": "0dp" },
  "shell": { "skin": "kitkat", "accent": "#FF33B5E5", "bar": "#FF000000",
             "layout": "system_bar", "bar_apps": false,
             "bar_home": "gradient" }
}
```

`themes/theme.schema.json` describes the same thing for editors; `theme_tool.py check` enforces it.

### Identity

- `schema`: always `1` for this version of the format.
- `id`: the folder name. Lowercase letters, digits and `_`, starting with a letter. It ends up in
  package names and in the `retro_ui_style` setting, so don't rename a pack once it ships.
- `name`, `summary`: what people see in Settings. The summary is one sentence: what era or idea
  the pack recreates and what stands out.
- `category`: the group in Settings. `modern` is today's Android, `android` a past Android
  release, `community` a ROM or community classic.
- `order`: position inside the category. Android eras use their version (`44` for 4.4).
- `authors`: who made it.

### Palette

Android 12 and later color almost everything from a palette that SystemUI derives from one seed
color (Monet). A pack picks the seed and how it's turned into palettes.

- `"source": "wallpaper"`: keep the user's own Material You colors. Use it when the pack is about
  something other than color (Pitch Black, Glass).
- `"source": "preset"` with `seed` (`#RRGGBB`) and `style`:

  | Style | Result |
  |---|---|
  | `TONAL_SPOT` | Calm, close to the seed. Today's default. |
  | `VIBRANT` | Saturated accents. Good for Holo blue and other bright eras. |
  | `EXPRESSIVE` | Shifts the hue for a playful palette. |
  | `SPRITZ` | Nearly grey with a hint of the seed. |
  | `RAINBOW`, `FRUIT_SALAD` | Multi-hue palettes. |
  | `MONOCHROMATIC` | Greys only. |

Monet derives palettes; it doesn't copy your seed exactly. The seed shows up as a tone of the
primary color, and the rest is computed for contrast. If an exact color matters (a panel
background, the system bar), set it in `shell`, which is drawn as given.

### Light and dark

- `night`: `dark` or `light` turns that mode on while the pack is active. `system` leaves the
  user's setting alone. RetroStyle saves the user's own mode the first time a pack forces one and
  gives it back when they switch to a `system` pack.
- `black`: pure black backgrounds in dark mode, through LineageOS's Black theme. It needs `night`
  `dark` or `system` and does nothing outside LineageOS.

Holo and the classic eras were dark. Material 1 (Lollipop) was light. Pick what the era looked
like by default.

Monet derives the greys from the seed too, so a strong seed tints every background. Eras with
black and grey surfaces and a colored accent only (Holo Dark: KitKat, Jelly Bean) set `black`,
which swaps the tinted neutrals for real black and grey and leaves the accent alone.

### Typography

`font` sets the four font roles framework-res exposes: `body`, `body_medium`, `headline` and
`headline_medium`. The values are font families the system already has:

`sans-serif` (Roboto), `sans-serif-thin`, `sans-serif-light`, `sans-serif-medium`,
`sans-serif-black`, `sans-serif-condensed` (and `-light`, `-medium`), `sans-serif-smallcaps`,
`sans-serif-monospace`, `serif`, `serif-bold`, `serif-monospace`, `monospace`, `casual`,
`cursive`, `roboto-flex`, `source-sans-pro`, `source-sans-pro-semi-bold`.

Google Sans isn't one of them (it only ships with Google's apps), and a pack can't add a new
font family yet. Droid Sans, the font of Android 1 and 2, would need that, so the classic packs
use Roboto.

### Shape

- `corner`: one radius, in dp, for every rounded surface: dialogs, buttons, bottom sheets,
  notifications, Quick Settings tiles, the shade panels, the power menu and Settings cards.
- `progress`: progress bar corners (default: `corner`). Holo progress bars were square (`0dp`).

The tool writes these into `config_dialogCornerRadius`, `notification_corner_radius`,
`qs_corner_radius`, `settingslib_preference_corner_radius` and the rest. One value keeps the
whole OS consistent; per-surface radii can still go in hand-made resources.

Era guide: `2dp` for Holo and Material 1, `4dp` for the rounded classic buttons, `8dp` for Pie,
and leave it out to keep Android 16's large radii.

### Widgets

Apps that use the platform's widgets (`Widget.DeviceDefault.*`, which includes Settings and every
framework dialog) draw buttons, checkboxes, switches, text fields, spinners, progress bars and
lists from one style family. `widgets` re-parents those styles to an older family that is still
inside framework-res:

| Value | Family | Era |
|---|---|---|
| `default` | today's DeviceDefault | Android 16 |
| `holo` | `Widget.Holo.*` (and `Widget.Holo.Light.*` in light mode) | Android 3.0 to 4.4 |
| `classic` | the original `Widget.*` styles | Android 1.0 to 2.3 |
| `material` | `Widget.Material.*` without the later DeviceDefault changes | Android 5.0 to 9 |

The tool reads `frameworks/base/core/res/res/values` of your tree and maps every
`Widget.DeviceDefault.*`, `TextAppearance.DeviceDefault.*` and `AlertDialog.DeviceDefault.*`
style that has a counterpart (200 for Holo). `Theme.DeviceDefault` itself is never changed:
SystemUI and Settings need its newer color attributes.

Apps that bundle their own widgets (AppCompat, Material Components, Jetpack Compose) keep them.
They still get the palette, fonts and dark mode.

### Icons

Icons are hand-made: put them in `icons_android/res/`, `icons_systemui/res/`,
`icons_settings/res/` or `icons_launcher/res/` under the same resource names the target uses. To
find a name, look in the target's `res/` folder in the source tree, or run
`aapt2 dump resources <target>.apk`.

Rules for icons:

- Prefer vector drawables (`drawable/*.xml`) in white (`#FFFFFFFF`). SystemUI tints most icons
  itself; a colored icon looks wrong when it does.
- Bitmaps are fine for era-accurate artwork. Ship them for one density (`drawable-xhdpi` or
  `drawable-xxhdpi`) and Android scales them. Don't mix densities of the same icon.
- The system bar tray lives in Launcher3. Its icons (`ic_retro_tray_battery`,
  `ic_retro_tray_wifi` and the navigation glyphs) go in `launcher3/res/`. KitKat and Jelly Bean
  show how: level-list drawables pointing at per-level images.

### Shell

The shell is SystemUI's shade and Launcher3's bottom bar. The retro-style patches draw it, and a
pack chooses from what they offer:

- `skin`: `stock` (Android 16's shade) or `kitkat` (the KitKat tablet panels: a list-style Quick
  Settings with a big clock header, and the top status bar hidden on the tablet system bar).
- `panel`: the shade panels' background, `#AARRGGBB`. Alpha below `FF` lets the wallpaper (and
  blur, where the build has it) show through. Leave it out for the skin's own color.
- `accent`: the `kitkat` skin's clock and switch color (default Holo blue).
- `bar`: the taskbar and tablet system bar background.
- `layout`: the bottom bar the pack suggests, `stock` or `system_bar`. Builds use it as the
  default when they don't set one; users can always change it.
- `bar_apps`: whether app icons show on the tablet system bar by default.
- `bar_home`: the tablet system bar's background on the home screen: `solid` (the `bar` color),
  `gradient` (a shadow fading to dark at the bottom, as Android 4.x drew behind its navigation
  buttons), `translucent` (the `bar` color at half opacity) or `transparent` (none). Leave it out
  for the launcher's own. Apps always keep the launcher's own background. A build can override
  every pack's choice with `RETRO_STYLE_BAR_BACKGROUND`.

A new skin (a Gingerbread-style shade, say) is code, not a theme: it needs a patch. Packs can
only pick skins that exist.

### Hand-made resources

Anything else goes in `<target>/res/` as normal Android resources with the target's names:
colors, sizes, drawables, strings. Some guidance:

- Don't repeat a value `theme.json` already sets. `check` fails when a name is both hand-made
  and generated.
- Only override names the target has. `check` lists every name the target lacks. New names are
  fine only when your own files use them (KitKat's `kitkat_tray_*` images, used by its tray
  level-lists).
- Overlays can't add layouts or views that don't exist, and can't change code. If you need
  that, it's a patch.
- Cover every folder the target uses for a resource. An overlay only replaces the variants it
  also defines: if the target has `values-night/` or `color-night-v31/` versions (Launcher3's
  taskbar colors do), a plain `values/` entry loses in dark mode. Look the name up with
  `aapt2 dump resources <target>.apk` and add the same folders (`values-night/`, ...).
  `theme_tool.py` already does this for the colors it generates.
- Fewer overrides survive Android updates better. Every hand-made name is one that can be
  renamed or removed in the next release.

## Rules every pack follows

1. **Readable.** Text keeps a contrast of at least 4.5:1 on its background. `check` computes it
   for `shell.panel` over a mid-grey wallpaper (the worst common case for translucent panels),
   against white text in dark mode and near black in light mode.
2. **Licensed.** Every image and font has a source you may redistribute, listed in `SOURCES.md`.
   AOSP artwork is Apache 2.0: import it with `assets/import_aosp_assets.sh` and let
   `theme_tool.py sources <id>` write the file. Original artwork gets a hand-written
   `SOURCES.md`. Paid or unlicensed themes (most Substratum themes, TabletKat) are inspiration
   only: recreate the look, never copy files.
3. **Named honestly.** Palettes from other projects (Nord, Dracula, Solarized, Gruvbox,
   Catppuccin) say so in the summary, with their license.
4. **Complete for its layers.** If a pack changes widgets, it also sets a palette and dark mode
   that suit them; Holo widgets on a light pastel palette look broken, not retro.
5. **Built with the tool.** Generated files come from `theme_tool.py gen` and are committed. Never
   edit them by hand.

## Checking a pack

```sh
tools/theme_tool.py gen <id>        # after every theme.json change
tools/theme_tool.py check <id>      # fields, generated files, resource names, contrast
tools/theme_tool.py check <id> --apks out/target/product/<device>   # also links like the build
```

`check` reads resource names from the source tree (so names added by the retro-style patches
count) and, with `--apks`, from the built APKs. With `--apks` it also compiles and links every
overlay with aapt2 against the built framework, using the same flags as Soong.

Then try it on a device: build, flash, pick it in Settings, and run
`tests/retro_style_matrix.sh` ([TESTING.md](TESTING.md)), which applies every installed pack and
checks overlays, restarts and crashes.

## Shipping packs

`retro_style.mk` installs every pack by default. A ROM can ship fewer:

```make
RETRO_STYLE_THEMES := material m3 kitkat jellybean
RETRO_STYLE_TYPE := jellybean       # the default pack
$(call inherit-product, vendor/retro-style/retro_style.mk)
```

Each pack is one Soong module, `RetroTheme_<id>`, which pulls in its `theme.json` (installed to
`/product/etc/retro-style/themes/<id>.json`) and its overlays. The RetroStyle app lists whatever
it finds there, so a pack added to the tree shows up in Settings with no app change.

## What's in the box

| Pack | Category | Recreates |
|---|---|---|
| `material` | modern | Android 16 as shipped, wallpaper colors |
| `m3` | modern | Material 3 with the fixed baseline palette |
| `froyo` | android | Android 2.2: classic widgets, orange highlights, rounded buttons |
| `gingerbread` | android | Android 2.3: classic widgets on black, green |
| `honeycomb` | android | Android 3: Holo Dark, navy panels, the tablet system bar |
| `jellybean` | android | Android 4.0 to 4.3: Holo Dark, blue status icons, Jelly Bean tablet glyphs |
| `kitkat` | android | Android 4.4: Holo Dark, KitKat glyphs and panels, the tablet system bar |
| `holo_light` | android | Android 4.x light apps: Holo Light |
| `lollipop` | android | Android 5 and 6: Material 1, light, indigo, 2dp corners |
| `pie` | android | Android 9: rounded Material, Google blue, 8dp corners |
| `black` | community | Pitch Black: pure black for OLED |
| `transparent` | community | Glass: translucent shade and bar |
| `mono` | community | Monochrome |
| `nord`, `dracula`, `solarized`, `gruvbox`, `catppuccin` | community | Popular editor palettes |

## Limits worth knowing

- **Apps with their own design system** (AppCompat, Material Components, Compose, Flutter,
  games) only follow the palette, fonts and dark mode. Nothing a system theme can do reaches
  further without per-app overlays.
- **Compose parts of SystemUI** (Quick Settings tiles, the shade panels) take colors and sizes
  from resources and from `retro_ui_applied`, not from layouts, so their structure only changes
  through skins.
- **Exact palette colors.** Monet derives tones from the seed. Pinning every tone would compete
  with SystemUI's own color overlays, so packs set exact colors only where the shell draws them.
- **Fonts** are limited to the system's families until packs can ship their own.
