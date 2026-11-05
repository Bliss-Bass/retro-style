# How retro-style works

retro-style changes the look of the whole OS without forking it. It combines five standard
Android mechanisms, and each one is worth knowing on its own:

1. **Runtime resource overlays (RROs)** replace resources (styles, dimensions, drawables,
   colors) in other packages without touching their code.
2. **SystemUI's theme JSON** turns a seed color, a font and icon packs into overlays for every app.
3. **aconfig flags set at build time** turn on SystemUI features that are off in the release.
4. **A privileged platform app** stores the user's choices and switches all of the above.
5. **Small patch series** add what resources alone can't: new layouts in SystemUI and the
   taskbar.

```mermaid
flowchart LR
    user([User]) --> page[RetroStyle app<br/>Settings > System > UI style]
    page -->|"retro_* choices<br/>retro_ui_applied"| secure[(Settings.Secure)]
    page -->|"enable / disable"| om[OverlayManager]
    page -->|"theme JSON"| secure
    page -->|"restart when needed"| sysui
    page -->|"restart when needed"| launcher
    om --> rros[RROs in /product/overlay]
    secure -->|"retro_ui_applied<br/>(read at start)"| sysui[SystemUI<br/>patched]
    secure -->|"retro_ui_applied<br/>(read at start)"| launcher[Launcher3 taskbar<br/>patched]
    secure -->|"theme JSON"| toc[ThemeOverlayController<br/>in SystemUI]
    toc -->|"palette, font, icon packs"| allapps[All apps]
    rros --> fw[framework-res, SystemUI,<br/>Settings, Launcher3]
```

## 1. Runtime resource overlays

An RRO is an APK with only resources and a manifest naming its target:

```xml
<overlay android:targetPackage="com.android.launcher3" android:category="org.retro.theme" />
```

When it's enabled, the target sees the overlay's resources instead of its own wherever the names
match. Theme packs (`themes/<id>/`, see [THEME_SPEC.md](THEME_SPEC.md)) are mostly overlays. The
KitKat pack, for example:

- re-parents `Widget.DeviceDefault.*` styles in `framework-res` to their Holo versions
  (`themes/kitkat/android/res_gen/`, made by `tools/theme_tool.py` from `"widgets": "holo"`), so
  every app built on DeviceDefault widgets turns Holo;
- squares the corner radii in framework-res, SystemUI and Settings (from `"shape"`);
- swaps navigation and tray icons in Launcher3 (`themes/kitkat/launcher3/res/drawable*`).

A designer writes `theme.json` and any hand-made resources; `theme_tool.py gen` writes the
overlay manifests, the generated resources and the `Android.bp`, so every pack is built the same
way.

Two rules decide what an overlay may touch:

- **Where it's installed.** The overlays here are `product_specific: true`, so they land in
  `/product/overlay`. Overlays on system partitions are trusted more than ones installed later.
- **What the target allows.** A target can declare `<overlayable>` resources and policies. Targets
  without any declaration (framework-res, SystemUI, Settings and Launcher3 today) accept any
  overlay from a system partition or signed with the same key. That's why these overlays only
  work when built into the image; installing them later with `adb install` fails for Launcher3.

Overlays with `android:isStatic="true"` (the Dual Shade config) are always on. The others start
disabled. RetroStyle turns on the chosen pack's `org.retro.theme` overlays and turns off every
other pack's through `OverlayManager`; SystemUI switches the font and icon pack overlays (next
section).

## 2. The theme JSON

SystemUI's `ThemeOverlayController` watches the secure setting
`theme_customization_overlay_packages`, a small JSON object. From a seed color and a style it
builds Material You's color palette for every app (Monet), and it enables the font and icon pack
overlays the JSON names. This is what the stock Wallpaper & style picker writes too.

RetroStyle builds the JSON from the pack, on top of the user's own JSON (saved in
`retro_ui_style_saved_theme` before the first switch):

| Pack field | Theme JSON |
|---|---|
| `"palette": {"source": "preset", ...}` | `system_palette` (the seed), `color_source` `preset`, `theme_style` |
| a `font` overlay | `font`: `org.retro.theme.<id>.font` |
| `icons_*` overlays | `icon_pack.android`, `icon_pack.systemui`, ...: `org.retro.theme.<id>.icons.*` |

A pack that sets none of these (Material You, Pitch Black, Glass) gives the user's own JSON back.
For KitKat that's seed `FF33B5E5` with `VIBRANT`, the KitKat font and the KitKat icon packs.

During the setup wizard, SystemUI writes hardware color defaults over this JSON. RetroStyle
schedules a job (`SetupCompleteJob`) that writes the pack's JSON again once
`user_setup_complete` turns 1.

Packs can also force dark or light mode (`UiModeManager`) and turn on LineageOS's pure black
theme (the `berry_black_theme` setting). RetroStyle keeps the user's own values in
`retro_ui_style_saved_night` and `retro_ui_style_saved_black` and puts them back when the user
picks a pack that leaves them alone.

## 3. Dual Shade and aconfig

Dual Shade (separate floating Notifications and Quick Settings panels) is SystemUI's newer shade,
built on its "scene container". It's behind two aconfig flags, `scene_container` and
`status_bar_for_desktop`, which are off in the `bp4a` release.

aconfig flags get their values from the release config. `config/aconfig/` sets both flags:

- `*_flag_values.textproto`: one value per flag
- `Android.bp`: `aconfig_values` and an `aconfig_value_set` that groups them
- `release_config_map.textproto` and `release_configs/bp4a.textproto`: add the value set to the
  `bp4a` release
- `retro_style.mk`: `PRODUCT_RELEASE_CONFIG_MAPS` adds the map to your product

Values set at build time work everywhere. On-device overrides (`aflags`, `device_config`) need
the `aconfigd` service and a `/metadata` partition, which not every device has (x86 images don't).

Turning on the scene container exposes some unfinished parts of AOSP. Patches 0004, 0005 and 0007
fix the ones we hit: stuck heads-up notifications, the shade window swallowing taps, and a
lockscreen crash loop ([PATCHES.md](PATCHES.md)).

## 4. The RetroStyle app

`RetroStyle/` is a privileged system app signed with the platform key and running as the system
user (`android:sharedUserId="android.uid.system"`). That's what lets it switch other packages'
overlays (`CHANGE_OVERLAY_PACKAGES`), write secure settings, restart SystemUI and the launcher
(`KILL_UID`), and list other apps' windows (`REAL_GET_TASKS`).

| Source | Job |
|---|---|
| `ThemeCatalog.kt` | The installed packs, read from `/product/etc/retro-style/themes/<id>.json` |
| `StyleSettings.kt` | The user's choices (`retro_*` secure settings) and their defaults |
| `StyleApplier.kt` | Resolves the choices, writes `retro_ui_applied`, switches overlays, writes the theme JSON, sets dark mode, restarts what needs restarting |
| `WindowSession.kt` | Saves and reopens desktop windows around a SystemUI restart |
| `ApplyWorker.kt` | Runs applies off the main thread; boot, adb/test and setup-complete entry points |
| `StyleActivity.kt` | The Settings page: lists the packs by category and is themed after the chosen one |
| `Hidden.kt` | The few hidden framework calls, through reflection (see below) |

### Why a secure setting and not system properties

SystemUI and Launcher3 need to know the applied style when they start. A system property would
be the obvious channel, but on SELinux-enforcing builds an app can't set a custom `persist.*`
property without new SELinux policy. Secure settings need no policy: RetroStyle writes the
resolved style to one setting, `retro_ui_applied`
(`style=kitkat;theme=jellybean;variant=kitkat_stock;layout=system_bar;anchor=bottom_end;`
`bar_apps=0;bar_background=gradient;panel_color=;accent=#FF33B5E5;...`), and SystemUI and Launcher3 each read it once
through a small `RetroUiApplied` helper the patches add. `style` is the shell skin the patches
draw (`kitkat`, or anything else for stock) and `theme` the pack it came from; `panel_color` and
`accent` are the pack's shell colors.
Reading a read-only build property (`ro.retro.ui_style.default`) is allowed for every app, so the
build defaults stay properties.

### Why SystemUI and the launcher restart

Both read the applied style once per process, as Android does for most configuration. When a
value they use changes (the skin, the panel colors, the panel anchor, the status bar overlay, the
bar layout or its options), RetroStyle kills the process and Android restarts it: SystemUI because
it's persistent, the launcher because it's home.

### Keeping the user's windows

On large screens with desktop windowing, desktop windows live in root tasks that WM Shell
creates, and WM Shell runs inside SystemUI. When SystemUI dies, WindowManager removes those tasks
and closes every desktop window. `WindowSession` lists the visible desktop windows (component and
bounds) before the restart, waits for the new SystemUI and a few seconds for WM Shell to set the
desktop up again, then reopens each window at its old bounds (`ActivityOptions.setLaunchBounds`).
Apps reopen at their main screen; the UI style page reopens on itself.

### Hidden APIs and two builds

Soong builds the app with `platform_apis: true`, which can call hidden framework APIs directly.
The repo also has a Gradle build (`RetroStyle/`, `./gradlew :app:assembleRelease`) for quick
iteration, which only sees the public SDK. To keep one source for both, the handful of hidden
calls (`OverlayManager.setEnabled`, `ActivityManager.killUid`, a task's window bounds) go through
reflection in `Hidden.kt`. Platform-signed apps are exempt from hidden API restrictions, so this
works on the device. `app/hidden-api-stub` gives the Gradle build `android.os.SystemProperties`.

## 5. The patches

Resources can't add a layout that doesn't exist, so SystemUI and the taskbar get small patch
series. They're kept as `git format-patch` files so you can read them, apply them with `git am`,
and regenerate them from commits. [PATCHES.md](PATCHES.md) goes through each one.
