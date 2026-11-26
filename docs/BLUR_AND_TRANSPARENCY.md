# Blur and transparency

How Glass-like see-through panels, dialogs and freeform windows work with retro-style.
Theme packs supply **defaults**; **Settings > System > UI style** (and Display >
Appearance > UI style) owns **user overrides**. SystemUI, Launcher3 and SmartDock read the
resolved values from `retro_ui_applied`.

## Phase status

| Phase | Scope | Status |
|-------|--------|--------|
| 0 | Architecture docs + token stubs | done |
| 1 | System chrome (Dual Shade, All Apps / start, SmartDock panels) + UI Style panel prefs | done |
| 2 | System dialogs + UI Style dialog prefs | done |
| 3 | Freeform / window backgrounds + UI Style window prefs | done |
| 4 | Doc polish, troubleshooting, tests | done |

### Shipped touchpoints (phases 1–3)

- RetroStyle: `StyleSettings` / `StyleDraft` / expanding Advanced with Material opacity/blur
  sliders; Glass `themes/transparent/theme.json` defaults; `StyleApplier` → `retro_ui_applied`.
- SystemUI: `RetroUiStyle` blur/dialog/window keys; Dual Shade `OverlayShade` panel-local `BackgroundBlurDrawable`; overlay window blur skipped when `panel_blur=1`; SystemUI dialog bottom sheets honor dialog blur/alpha.
- Launcher3: All Apps max blur radius from `panel_blur_radius` when `panel_blur=1`.
- SmartDock: notification panel uses `BlurUtils` when `enable_background_blur` is on.
- WM Shell: focused freeform task leash blur/alpha from `window_*` keys (`RetroUiWindowStyle`,
  max 40dp blur); finishT / transition finish re-applies alpha; media/camera denylist.

## Gates (do not use the old RRO bool)

Cross-window blur is enabled only when all of these hold:

- `ro.surface_flinger.supports_background_blur=1` (ax86-lite `--blurui`)
- `Settings.Global.DISABLE_WINDOW_BLURS` off
- Device not in power-save / thermal blur disable (`BlurController`)
- SystemUI: `ActivityManager.isHighEndGfx()`, `persist.sysui.disableBlur` unset
- Launcher3: `WindowBlurState` / `BlurUtils.supportsBlursOnWindows()`

SmartDock’s `BlurOverlay` RRO still sets `config_backgroundBlurSupported`. That resource is
**dead on this tree** — nothing in frameworks reads it. Prefer `--blurui` / the SurfaceFlinger
property.

## Prefer localized blur

| API | Scope | Use for |
|-----|--------|---------|
| `ViewRootImpl.createBackgroundBlurDrawable()` | View bounds + corners | SmartDock panels, Dual Shade panel chrome, folders |
| `Window.setBackgroundBlurRadius` / `windowBackgroundBlurRadius` | Window bounds | System dialogs with a DecorView |
| `SurfaceControl.Transaction.setBackgroundBlurRadius` | Surface bounds | Launcher All Apps overlay, WM Shell task leash |
| `FLAG_BLUR_BEHIND` | Fullscreen dim layer | Avoid for floating panels; OK for intentional fullscreen modals (power menu) |

Cost scales with blurred **area** and forces GPU composition. Old Bliss-11 fullscreen RRO blur
is inefficient; do not revive it as the primary path.

## Pack defaults vs UI Style prefs

```
theme.json shell.*  ──┐
                      ├── StyleApplier ── retro_ui_applied ── SystemUI / Launcher3 / SmartDock / WM
StyleSettings keys ───┘
```

Resolution (same as `bar_background` / `bar_apps`):

1. If the StyleSettings secure key is set → user value.
2. Else if the active pack’s `shell.*` field is set → pack default.
3. Else → opaque / blur-off stock behavior.

Selecting the **Glass** pack (`transparent`) only pre-fills pack defaults. Any pack can get the
same look from UI Style **Transparency & blur** controls. Clearing a pref restores the pack
default.

### Settings and applied keys

| Phase | Secure setting | Pack default | `retro_ui_applied` key | Meaning |
|-------|----------------|--------------|------------------------|---------|
| 1 | `retro_ui_panel_color` | `shell.panel` | `panel_color` | Shade / chrome panel fill `#AARRGGBB` |
| 1 | `retro_ui_panel_blur` | `shell.blur_enabled` | `panel_blur` | `1`/`0` enable localized panel blur |
| 1 | `retro_ui_panel_blur_radius` | `shell.blur_radius` | `panel_blur_radius` | Radius in dp (e.g. `25`) |
| 2 | `retro_ui_dialog_alpha` | `shell.dialog_alpha` | `dialog_alpha` | Dialog surface alpha `0`–`255` or `#AARRGGBB` |
| 2 | `retro_ui_dialog_blur_radius` | `shell.dialog_blur_radius` | `dialog_blur_radius` | Dialog window blur radius dp |
| 3 | `retro_ui_window_alpha` | `shell.window_alpha` | `window_alpha` | Freeform / DeviceDefault window alpha |
| 3 | `retro_ui_window_blur_radius` | `shell.window_blur_radius` | `window_blur_radius` | Task / window blur radius dp |

Bar home translucency already uses `retro_ui_bar_background` (`translucent` / `transparent`).

## Surfaces today

### SmartDock

[`BlurUtils.kt`](../../packages/apps/SmartDock-DFC/app/src/main/java/cu/axel/smartdock/utils/BlurUtils.kt)
applies `BackgroundBlurDrawable` to dock / apps / QS / context menus when
`enable_background_blur` or `enable_dock_blur` is on. The notification panel must use the same
`showAppMenu` path (phase 1). Window format is `TRANSLUCENT`; panel alpha is capped so blur
stays visible.

### Dual Shade (SystemUI)

Panels take fill from `panel_color` / KitKat defaults
([`RetroUiStyle`](../../frameworks/base/packages/SystemUI/src/com/android/systemui/shade/shared/model/RetroUiStyle.kt)).
Stock blur is still largely **window-wide** via shade `SurfaceControl` +
`notification_shade_blur`. Phase 1 adds panel-local blur when `panel_blur=1`, clipped to the
panel rect (`onBackgroundPlaced` / panel view bounds).

### Launcher3 All Apps / start

[`TaskbarOverlayController.setBackgroundBlurRadius`](../../packages/apps/Launcher3/quickstep/src/com/android/launcher3/taskbar/overlay/TaskbarOverlayController.java)
blurs the overlay window. Prefer that (or a panel-sized surface) over full workspace depth blur;
keep `reduce_workspace_blur_usage`. Phase 1 can scale radius from `panel_blur_radius`.

### System dialogs (phase 2)

Prefer `Window.setBackgroundBlurRadius` / theme `windowBackgroundBlurRadius` with translucent
`windowBackground`. Honor `isCrossWindowBlurEnabled` with an opaque fallback.

### Freeform windows (phase 3)

Optional WM Shell task-leash blur via `SurfaceControl.setBackgroundBlurRadius`, capped to
focused / small sets of windows. `Theme.DeviceDefault` overrides only reach apps that use them;
AppCompat / Material / Compose / Flutter often ignore windowBackground.

## Acceptance criteria (by phase)

**Phase 1:** Dual Shade panes, All Apps, and SmartDock notification panel blur behind their own
rounded rects when blur is on; UI Style can set panel color/alpha and blur without picking Glass;
Glass pack still defaults to translucent panels + blur on.

**Phase 2:** System-owned dialogs honor dialog alpha/blur prefs; opaque fallback when blur is
disabled by the system.

**Phase 3:** Freeform chrome can take window alpha/blur from prefs with cost caps; docs state
app-reach limits.

**Phase 4:** This file, `THEME_SPEC.md`, and `USER_GUIDE.md` fully list keys; troubleshooting
covers `--blurui` and thermal/power-save.

## Troubleshooting

| Symptom | Check |
|---------|--------|
| No blur anywhere | `--blurui` / `getprop ro.surface_flinger.supports_background_blur` |
| Blur vanishes on battery saver | Expected (`BlurController`) |
| Pref set but UI unchanged | Apply UI style; SystemUI/Launcher restart; `settings get secure retro_ui_applied` |
| Opaque panel hides blur | Lower panel alpha (`#8C…` / UI Style), do not use `#FF…` |
