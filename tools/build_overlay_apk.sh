#!/usr/bin/env bash
# Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial
# Build and platform-sign one overlay from overlays/<Name> with aapt2, for quick
# adb testing without a full build. Images build the same sources through each
# overlay's Android.bp (runtime_resource_overlay) instead.
#
# Usage: tools/build_overlay_apk.sh <Name> <framework-res.apk> [out-dir]
#   framework-res.apk: pulled from the device (adb pull /system/framework/framework-res.apk)
#   so overlays can reference private @*android: resources.
# Environment:
#   AOSP  source tree root (default: two levels up, i.e. this repo at vendor/retro-style)
#   KEYS  directory with the platform.pk8 / platform.x509.pem your build signs with
#         (default: build/make/target/product/security, the AOSP test keys)
# The device only accepts the overlay if KEYS matches the key its framework was signed with.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
name=$1
fw=$2
out="${3:-/tmp/retro-style-overlays}"
aosp="${AOSP:-$here/../..}"
keys="${KEYS:-$aosp/build/make/target/product/security}"
aapt2="${AAPT2:-$aosp/prebuilts/sdk/tools/linux/bin/aapt2}"
hostlib="$aosp/out/host/linux-x86/lib64"
src="$here/overlays/$name"
work="$out/$name"

rm -rf "$work"
mkdir -p "$work/compiled"
"$aapt2" compile --dir "$src/res" -o "$work/compiled"
"$aapt2" link -o "$work/unsigned.apk" -I "$fw" --manifest "$src/AndroidManifest.xml" \
	--min-sdk-version 36 --target-sdk-version 36 --no-auto-version \
	"$work"/compiled/*.flat
LD_LIBRARY_PATH="$hostlib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
	java -Djava.library.path="$hostlib" -jar "$aosp/out/host/linux-x86/framework/signapk.jar" \
	"$keys/platform.x509.pem" "$keys/platform.pk8" "$work/unsigned.apk" "$out/$name.apk"
echo "$out/$name.apk"
