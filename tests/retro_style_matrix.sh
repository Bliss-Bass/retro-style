#!/system/bin/sh
# Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial
# retro_style_matrix.sh
# Bug hunt for Settings > System > UI style: applies combinations of the
# retro_* settings, then checks the applied style, overlays, SystemUI / Launcher3
# stability, crashes and ANRs, both shade panels, and that an open app window
# survives the restarts. Restores the previous settings at the end.
#
# Run as root on a build with vendor/retro-style:
#   adb root
#   adb push vendor/retro-style/tests/retro_style_matrix.sh /data/local/tmp/
#   adb shell sh /data/local/tmp/retro_style_matrix.sh [quick|full]
# Each combination is applied the way the Settings page does it: write the
# secure settings, then ask the RetroStyle app to apply them (its APPLY
# broadcast returns when the apply, restarts included, has finished).
# Results: /data/local/tmp/retro_matrix/results.tsv, summary.txt, crash logs and
# screenshots per failing combination.

OUT=/data/local/tmp/retro_matrix
MODE=${1:-full}
SETTLE=${SETTLE:-6}
rm -rf "$OUT"
mkdir -p "$OUT"
RESULTS=$OUT/results.tsv
SUMMARY=$OUT/summary.txt
TEST_APP=com.android.settings/com.android.settings.homepage.SettingsHomepageActivity

secure() { v=$(settings get secure "$1"); [ "$v" = null ] && v=""; echo "$v"; }
put() { if [ -n "$2" ]; then settings put secure "$1" "$2"; else settings delete secure "$1" >/dev/null; fi; }
overlay_on() { cmd overlay list 2>/dev/null | grep -q "^\[x\] $1\$" && echo 1 || echo 0; }
pid_of() { pidof "$1" 2>/dev/null | awk '{print $1}'; }

if ! pm path org.retro.style >/dev/null 2>&1; then
	echo "RetroStyle (org.retro.style) is not installed: not a retro-style build" >&2
	exit 2
fi

UI_NIGHT=$(cmd uimode night 2>/dev/null)
KEYS="retro_ui_style retro_ui_layout retro_panel_anchor retro_ui_style_variant retro_ui_hide_status_bar retro_ui_bar_apps retro_ui_bar_apps_gravity retro_ui_bar_buttons"
SAVED=""
for k in $KEYS; do SAVED="$SAVED $k=$(secure "$k")"; done

apply_and_wait() {
	am broadcast -a org.retro.style.action.APPLY -n org.retro.style/.ApplyReceiver >>"$OUT/apply.log" 2>&1
	sleep "$SETTLE"
}

# The resolved style the app wrote for SystemUI and Launcher3.
applied() { secure retro_ui_applied | tr ';' '\n' | sed -n "s/^$1=//p"; }

# Installed theme packs (themes/<id>/theme.json, installed by the build).
PACK_DIR=/product/etc/retro-style/themes
PACKS=$(ls "$PACK_DIR" 2>/dev/null | sed -n 's/\.json$//p')
pack_json() { tr -d '\n ' <"$PACK_DIR/$1.json" 2>/dev/null; }
# The pack's shell skin as the app writes it: kitkat, else material (the stock skin).
pack_skin() { pack_json "$1" | grep -q '"skin":"kitkat"' && echo kitkat || echo material; }
# The pack's Monet seed without '#', empty for wallpaper colors.
pack_seed() { pack_json "$1" | grep -q '"source":"preset"' && pack_json "$1" | sed -n 's/.*"seed":"#\([0-9A-Fa-f]*\)".*/\1/p'; }
# Every pack's switched overlays: on for [1], off for the others.
check_pack_overlays() {
	for p in $PACKS; do
		want=0
		[ "$p" = "$1" ] && want=1
		for t in android systemui settings launcher3; do
			cmd overlay list 2>/dev/null | grep -q " org\.retro\.theme\.$p\.$t\$" || continue
			[ "$(overlay_on "org.retro.theme.$p.$t")" = "$want" ] || fail "overlay org.retro.theme.$p.$t is not $want"
		done
	done
}

# One combination: style layout anchor variant hide apps gravity buttons
n=0
fails=0
echo "#	style	layout	anchor	variant	hide	apps	gravity	buttons	result	problems" >"$RESULTS"
run_combo() {
	style=$1 layout=$2 anchor=$3 variant=$4 hide=$5 apps=$6 gravity=$7 buttons=$8
	n=$((n + 1))
	problems=""
	fail() { problems="$problems; $*"; }

	# An app window to follow across the restarts.
	am start -W -n "$TEST_APP" >/dev/null 2>&1
	sleep 1
	logcat -b crash -c 2>/dev/null
	logcat -b events -c 2>/dev/null
	logcat -c 2>/dev/null

	put retro_ui_style "$style"
	put retro_ui_layout "$layout"
	put retro_panel_anchor "$anchor"
	put retro_ui_style_variant "$variant"
	put retro_ui_bar_apps "$apps"
	put retro_ui_bar_apps_gravity "$gravity"
	put retro_ui_bar_buttons "$buttons"
	put retro_ui_hide_status_bar "$hide"
	apply_and_wait

	# The applied style SystemUI and Launcher3 read: the pack id in `theme`, its shell skin in
	# `style`.
	[ "$(applied theme)" = "$style" ] || fail "applied theme=$(applied theme)"
	skin=$(applied style)
	[ "$skin" = "$(pack_skin "$style")" ] || fail "applied style (skin)=$skin"
	[ "$(applied layout)" = "$layout" ] || fail "applied layout=$(applied layout)"
	want_anchor=$anchor
	[ -z "$want_anchor" ] && { [ "$layout" = system_bar ] && want_anchor=bottom_end || want_anchor=top_end; }
	[ "$(applied anchor)" = "$want_anchor" ] || fail "applied anchor=$(applied anchor) want $want_anchor"
	[ -z "$apps" ] || [ "$(applied bar_apps)" = "$apps" ] || fail "bar_apps not applied"
	[ -z "$gravity" ] || [ "$(applied bar_apps_gravity)" = "$gravity" ] || fail "apps gravity not applied"
	want_buttons=$buttons
	[ "$want_buttons" = none ] && want_buttons=""
	[ -z "$buttons" ] || [ "$(applied bar_buttons)" = "$want_buttons" ] || fail "bar buttons not applied"

	# Overlays: only the chosen pack's org.retro.theme overlays are on.
	check_pack_overlays "$style"
	want_sb=0
	[ "$layout" = system_bar ] && { [ "$skin" = kitkat ] || [ "$hide" = 1 ]; } && want_sb=1
	[ "$(overlay_on org.retro.layout.systembar.android)" = "$want_sb" ] || fail "status bar overlay state wrong"
	seed=$(pack_seed "$style")
	if [ -n "$seed" ]; then
		secure theme_customization_overlay_packages | grep -qi "FF$seed" ||
			fail "theme JSON lacks the pack's seed $seed"
	fi

	# Stability: both processes up and not restarting.
	sui=$(pid_of com.android.systemui)
	l3=$(pid_of com.android.launcher3)
	[ -n "$sui" ] || fail "SystemUI not running"
	[ -n "$l3" ] || fail "Launcher3 not running"
	if [ "$layout" = system_bar ] && ! dumpsys window windows 2>/dev/null | grep -q "Window{.* Taskbar}"; then
		fail "no Taskbar window on the system bar"
	fi

	# Both panels, then close.
	for panel in expand-settings expand-notifications; do
		cmd statusbar "$panel" >/dev/null 2>&1
		sleep 2
		screencap -p "$OUT/c${n}_$panel.png" 2>/dev/null
		cmd statusbar collapse >/dev/null 2>&1
		sleep 1
	done
	sleep 2
	[ "$(pid_of com.android.systemui)" = "$sui" ] || fail "SystemUI restarted after settling (crash?)"
	[ "$(pid_of com.android.launcher3)" = "$l3" ] || fail "Launcher3 restarted after settling (crash?)"

	# Session: the test app window should still be there.
	am stack list 2>/dev/null | grep -F ": $TEST_APP " | grep -q "visible=true" ||
		fail "app window lost"

	crashes=$(logcat -b crash -d 2>/dev/null | grep -c "FATAL EXCEPTION")
	anrs=$(logcat -b events -d 2>/dev/null | grep -c "am_anr")
	[ "$crashes" = 0 ] || fail "$crashes crash(es)"
	[ "$anrs" = 0 ] || fail "$anrs ANR(s)"

	if [ -n "$problems" ]; then
		fails=$((fails + 1))
		result=FAIL
		logcat -b crash -d >"$OUT/c${n}_crash.txt" 2>/dev/null
		logcat -d -b main,system,events >"$OUT/c${n}_logcat.txt" 2>/dev/null
		screencap -p "$OUT/c${n}_screen.png" 2>/dev/null
	else
		result=PASS
		rm -f "$OUT/c${n}_"*.png
	fi
	printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$n" "$style" "$layout" \
		"${anchor:--}" "${variant:--}" "$hide" "${apps:--}" "${gravity:--}" "${buttons:--}" \
		"$result" "${problems#; }" >>"$RESULTS"
	echo "[$n] $result $style/$layout anchor=${anchor:-default} variant=${variant:-default} hide=$hide apps=${apps:-default} gravity=${gravity:-default} buttons=${buttons:-default}${problems:+ :: ${problems#; }}"
}

STYLES="material m3 kitkat"
[ "$MODE" = quick ] && STYLES="material kitkat"
for style in $STYLES; do
	[ -f "$PACK_DIR/$style.json" ] || { echo "pack $style is not installed" >&2; exit 2; }
done

# 1. Style x layout x anchor x status bar.
for style in $STYLES; do
	for layout in stock system_bar; do
		for anchor in "" top_end bottom_end; do
			for hide in 0 1; do
				[ "$layout" = stock ] && [ "$hide" = 1 ] && continue
				run_combo "$style" "$layout" "$anchor" "" "$hide" "" "" ""
			done
		done
	done
done

# 2. KitKat panel variants.
for variant in kitkat_stock holo_unified; do
	run_combo kitkat system_bar "" "$variant" 0 "" "" ""
done

# 3. System bar contents.
for style in $STYLES; do
	run_combo "$style" system_bar "" "" 0 0 "" none
	for gravity in start center end; do
		run_combo "$style" system_bar "" "" 0 1 "$gravity" none
	done
	run_combo "$style" system_bar "" "" 0 1 center vol_down,vol_up,screenshot,power
	[ "$MODE" = quick ] && continue
	run_combo "$style" system_bar "" "" 0 1 start vol_down,power
	run_combo "$style" system_bar "" "" 0 0 "" screenshot
done

# 4. Every installed pack, on the system bar (where the most shell options apply).
if [ "$MODE" != quick ]; then
	for pack in $PACKS; do
		run_combo "$pack" system_bar "" "" 0 "" "" ""
	done
fi

# 5. Rapid switching (no settle between applies).
SETTLE_SAVED=$SETTLE
SETTLE=0
for style in kitkat material kitkat m3; do
	put retro_ui_style "$style"
	apply_and_wait
done
SETTLE=$SETTLE_SAVED
run_combo material system_bar "" "" 0 1 center none

# Restore. Applying the saved pack also gives back the user's dark mode when that pack leaves
# it alone; the check below catches anything left over.
for kv in $SAVED; do put "${kv%%=*}" "${kv#*=}"; done
apply_and_wait
[ "$(cmd uimode night 2>/dev/null)" = "$UI_NIGHT" ] || echo "note: dark mode is now '$(cmd uimode night)', was '$UI_NIGHT'"

{
	echo "combinations: $n, failed: $fails"
	grep -P '\tFAIL\t' "$RESULTS" 2>/dev/null || awk -F'\t' '$10 == "FAIL"' "$RESULTS"
} | tee "$SUMMARY"
echo "results: $RESULTS"
