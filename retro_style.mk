# Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

#
# vendor/retro-style: retro UI styles (Material You, Material 3, KitKat) and the tablet
# system bar for large-screen Android 16 / LineageOS 23.2 builds.
#
# Use it from your device (or product) makefile:
#
#   # Optional build defaults, set BEFORE the inherit line:
#   RETRO_STYLE_TYPE := kitkat          # materialyou (default) | m3 | kitkat
#   RETRO_STYLE_LAYOUT := system_bar    # stock | system_bar (default: system_bar for kitkat)
#   $(call inherit-product, vendor/retro-style/retro_style.mk)
#
# The defaults only apply until the user picks something in Settings > System > UI style.
# The frameworks/base and Launcher3 patches must be applied too: see docs/INSTALL.md.
#

RETRO_STYLE_DIR := vendor/retro-style

# --- Build defaults ----------------------------------------------------------------------------

ifeq ($(RETRO_STYLE_TYPE),)
RETRO_STYLE_TYPE := materialyou
endif
ifeq ($(filter materialyou m3 kitkat,$(RETRO_STYLE_TYPE)),)
$(error RETRO_STYLE_TYPE must be materialyou, m3 or kitkat (got '$(RETRO_STYLE_TYPE)'))
endif

ifeq ($(RETRO_STYLE_TYPE),materialyou)
RETRO_UI_STYLE_DEFAULT := material
else
RETRO_UI_STYLE_DEFAULT := $(RETRO_STYLE_TYPE)
endif

# KitKat brings the tablet system bar unless the device asks for the stock taskbar.
ifeq ($(RETRO_STYLE_LAYOUT),)
ifeq ($(RETRO_UI_STYLE_DEFAULT),kitkat)
RETRO_STYLE_LAYOUT := system_bar
else
RETRO_STYLE_LAYOUT := stock
endif
endif
ifeq ($(filter stock system_bar,$(RETRO_STYLE_LAYOUT)),)
$(error RETRO_STYLE_LAYOUT must be stock or system_bar (got '$(RETRO_STYLE_LAYOUT)'))
endif

# Read-only properties in /system_ext/build.prop. The RetroStyle app reads them; any app may
# read an ro.* property, and nothing writes them.
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.retro.ui_style.default=$(RETRO_UI_STYLE_DEFAULT) \
    ro.retro.ui_layout.default=$(RETRO_STYLE_LAYOUT)

# --- The Settings page and the code that applies the style ------------------------------------

PRODUCT_PACKAGES += \
    RetroStyle

# --- Runtime resource overlays (overlays/<Name>/Android.bp) -----------------------------------

# KitKat style pack. RetroStyle enables the org.retro.style overlays; SystemUI applies the font
# and icon packs from the theme JSON RetroStyle writes.
PRODUCT_PACKAGES += \
    RetroStyleKitKatFramework \
    RetroStyleKitKatSystemUI \
    RetroStyleKitKatSettings \
    RetroStyleKitKatLauncher3 \
    RetroStyleKitKatFont \
    RetroStyleKitKatIconsAndroid \
    RetroStyleKitKatIconsSystemUI

# Tablet system bar: hides the top status bar when the tray shows the status icons.
PRODUCT_PACKAGES += \
    RetroStyleSystemBarFramework

# Dual Shade layout for SystemUI (always on).
PRODUCT_PACKAGES += \
    RetroStyleDualShadeSystemUI

# --- Dual Shade aconfig flags (config/aconfig) -------------------------------------------------

PRODUCT_RELEASE_CONFIG_MAPS += \
    $(RETRO_STYLE_DIR)/config/aconfig/release_config_map.textproto
