# The patches

`patches/<project>/NNNN-*.patch` are `git format-patch` files, one folder per git project in the
source tree. `tools/apply-patches.sh` applies them with `git am`; [INSTALL.md](INSTALL.md#2-apply-the-patches)
shows how.

They were made against LineageOS `lineage-23.2` and apply cleanly to its current tips of
`android_frameworks_base` and `android_packages_apps_Launcher3`.

**Apply both series together.** The two projects talk over AIDL interfaces that SystemUI owns:
Launcher3 0001 calls methods SystemUI 0001 adds to `ISystemUiProxy`, and Launcher3 0003 implements
the callback SystemUI 0003 adds to `ILauncherProxy`. Launcher3 builds against SystemUI's shared
library, so it won't compile without the SystemUI series.

## frameworks/base (SystemUI)

| # | Patch | What it does |
|---|---|---|
| 0001 | Open Dual Shade panels above a bottom tray | Adds `ShadePanelAnchor` (`anchor=bottom_end`): the floating panels open from the bottom right above a tray, and swiping down closes them. Adds `ISystemUiProxy` calls so the taskbar tray can open each panel on its own display. |
| 0002 | Add a KitKat skin for the Dual Shade panels | With `style=kitkat`: Quick Settings becomes a KitKat-style list of rows with Holo switches under a big blue clock, and the panels get Holo colors (`RetroUiStyle`, `KitKatTileList`). |
| 0003 | Send status bar icons to the launcher tray | Forwards the status bar's icons (Bluetooth, alarm, Do Not Disturb, ...) to the launcher over `ILauncherProxy`, so the tray can show them when the top status bar is hidden. |
| 0004 | Leave the boot lockscreen state when idle off Lockscreen | Scene-container fix: when SystemUI starts on a scene other than the lockscreen (no lockscreen, or during setup), the keyguard state stayed "lockscreen" forever, so heads-up notifications never timed out. |
| 0005 | Keep the desktop shade window untouchable over Gone | Scene-container fix: with the desktop status bar, a heads-up made the whole screen below the status bar touchable, so taps never reached apps until it left. |
| 0006 | Show the Quick Settings volume slider without tile details | The Dual Shade Quick Settings volume slider only appeared with the unfinished tile-details flag on. Shows it whenever `config_enableDesktopAudioTileDetailsView` is true (the Dual Shade overlay sets it). |
| 0007 | Keep a slot for missing lockscreen elements | Scene-container fix: the lockscreen layout expects six elements, but AOSP has no provider for the ambient indication area, so SystemUI crashed whenever the lockscreen showed. |
| 0008 | Read the applied retro style from Settings.Secure | Switches `RetroUiStyle` and `ShadePanelAnchor` from system properties to the `retro_ui_applied` secure setting RetroStyle writes ([why](HOW_IT_WORKS.md#why-a-secure-setting-and-not-system-properties)). |
| 0009 | Use the theme pack's shade panel and accent colors | Reads `panel_color` (the shade panels' background, both skins) and `accent` (the KitKat skin's clock and switches) from `retro_ui_applied`, so theme packs can color the shell ([THEME_SPEC.md](THEME_SPEC.md#shell)). |

0004, 0005 and 0007 fix AOSP bugs anyone turning on Dual Shade will hit. They don't depend on the
rest of retro-style.

## packages/apps/Launcher3 (the taskbar)

| # | Patch | What it does |
|---|---|---|
| 0001 | Add the tablet system bar layout | `RetroSystemBar` and `SystemBarNavLayoutter`: back/home/recents and optional extra buttons (volume, screenshot, sleep) at the start, and `TaskbarSystemTrayView` at the end with notifications, network, battery, volume and the clock. KitKat look with `style=kitkat`. |
| 0002 | Keep the colors of the KitKat tray status icons | The KitKat tray icons are full-color Holo images; don't tint them. |
| 0003 | Show SystemUI's status bar icons in the system bar tray | Receives the icons SystemUI 0003 sends and draws them in the tray. |
| 0004 | Keep a notifications bell in the system bar tray | The notifications target used to vanish with no notifications; now a bell keeps it reachable, and KitKat gets a divider between notifications and status. |
| 0005 | Keep system bar apps clear of the buttons and the tray | Desktop mode start-aligned the app icons over the left buttons. Lays them out between the buttons and the tray: at the start, centered (default) or at the end. |
| 0006 | Keep the system bar tray touchable without app icons | With no app icons showing, the taskbar only took touches over the nav buttons, so taps on the tray fell through. |
| 0007 | Keep the whole system bar in front of the shade | Opening a panel faded the app icons behind the shade's blur while the buttons stayed on top; on the system bar the whole bar stays in front. |
| 0008 | Read the applied retro style from Settings.Secure | Switches `RetroSystemBar` from system properties to `retro_ui_applied`. |
| 0009 | Let the system bar pick its home screen background | `RetroBarBackground`: with `bar_background` set, the system bar draws its own background on the home screen (solid, a 4.x shadow gradient, semi-transparent or transparent) instead of whatever the stock taskbar draws there. Apps keep the stock background. |

## Maintaining the series

The rule: **patches are always generated from commits, never edited by hand.** A hand-edited
patch can look right and still apply wrongly (bad hunk counts, stale blob ids). Keep the real
commits on a branch in each project and regenerate the files from them.

### Updating a patch

Say SystemUI 0002 needs a change:

```sh
cd frameworks/base
git log --oneline -9                      # find "SystemUI: Add a KitKat skin ..."
# make the change, then fold it into that commit:
git commit --fixup=<sha of 0002>
GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash <sha of 0001>
```

Then regenerate the whole series into this repo (it renumbers from 0001):

```sh
base=$(git merge-base HEAD m/lineage-23.2)   # or the upstream commit the series sits on
rm ../../vendor/retro-style/patches/frameworks/base/*.patch
git format-patch -o ../../vendor/retro-style/patches/frameworks/base "$base"..HEAD
```

### Adding a patch

Commit it on top of the series in the project, then regenerate as above. Write the commit message
for someone who has never seen the code: what was wrong or missing, and what the change does.

New files a patch adds start with retro-style's header (copyright Navotpala Tech, SPDX
`AGPL-3.0-only OR LicenseRef-Navotpala-Commercial`; copy it from `RetroUiApplied.kt`). Files the
patch only changes keep their AOSP Apache header. See [LICENSING.md](../LICENSING.md).

### Moving to a newer LineageOS

```sh
repo sync frameworks/base packages/apps/Launcher3
vendor/retro-style/tools/apply-patches.sh check
```

If `check` passes, apply and regenerate. If a patch conflicts, apply the series by hand with
`git am --3way`, resolve each conflict, then regenerate every patch from the resulting commits.

### Verifying

- `apply-patches.sh check` applies the series in a throwaway worktree.
- In the project, `git apply --check --reverse <patch>` passes when that patch is already in the
  tree.
- Build SystemUI and Launcher3 (`m SystemUI Launcher3QuickStep`) and run the matrix test
  ([TESTING.md](TESTING.md)).
