// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package android.os;

/**
 * Compile-only stub for the hidden android.os.SystemProperties, so the Gradle build can call it.
 * The device provides the real class; Soong builds (platform_apis: true) never see this file.
 */
public class SystemProperties {
    public static String get(String key, String def) {
        throw new RuntimeException("Stub!");
    }
}
