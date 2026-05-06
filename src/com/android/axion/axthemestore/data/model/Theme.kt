/*
 * Copyright (C) 2025 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.android.axion.axthemestore.data.model

data class ThemesResponse(
    val version: Int = 0,
    val lastUpdated: String = "",
    val themes: List<Theme> = emptyList(),
    val categories: List<ThemeCategory> = emptyList(),
    val components: List<ThemeComponent> = emptyList()
)

data class Theme(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val author: String = "",
    val version: String = "",
    val versionCode: Int = 0,
    val minSdk: Int = 31,
    val previewImages: List<String> = emptyList(),
    val category: String = "",
    val tags: List<String> = emptyList(),
    val overlays: List<ThemeOverlay> = emptyList(),
    val isUnified: Boolean = false,
    /** Preinstalled RRO on system image — no sideloading. */
    val isBundledOverlay: Boolean = false,
    val supportsRegionSampling: Boolean = false,
) {
    val totalFileSize: Long
        get() = overlays.sumOf { it.fileSize }
    
    val isUiStyle: Boolean
        get() = category == "ui_style"
    
    val uiStyleId: String?
        get() = when {
            id == "ui_style_axion" -> "axion"
            id == "ui_style_material3" -> "material3_expressive"
            id == "ui_style_minimal" -> "minimal"
            else -> null
        }
    
    val isLocal: Boolean
        get() = category == "local" || id.startsWith("local_")
}

data class ThemeOverlay(
    val componentId: String = "",
    val packageName: String = "",
    val targetPackage: String = "",
    val targets: List<String> = emptyList(),
    val downloadUrl: String = "",
    val fileSize: Long = 0,
    val enabled: Boolean = true
)

data class ThemeComponent(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val targetPackage: String = "",
    val icon: String = "palette"
)

data class ThemeCategory(
    val id: String = "",
    val name: String = "",
    val icon: String = "palette"
)

/**
 * Selection / apply state for preinstalled overlay packs (no APK install flow).
 */
sealed class ThemeSelectionState {
    /** This theme is currently applied (engine matches). */
    data object Active : ThemeSelectionState()

    /** Overlay package(s) are on device but this theme is not selected. */
    data object Inactive : ThemeSelectionState()

    /** One or more overlay packages are missing from the system image. */
    data object Missing : ThemeSelectionState()

    /** Last apply/disable operation failed. */
    data class Error(val message: String) : ThemeSelectionState()
}

fun Long.formatFileSize(): String {
    return when {
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> "${this / 1024} KB"
        else -> String.format("%.1f MB", this / (1024.0 * 1024.0))
    }
}

object StandardComponents {
    const val STATUSBAR_WIFI = "statusbar_wifi"
    const val STATUSBAR_SIGNAL = "statusbar_signal"

    const val ANDROID_FRAMEWORK = "android"
    const val SYSTEMUI = "systemui"

    const val UI_QS = "ui_qs"
    const val UI_VOLUME = "ui_volume"

    const val ICON_PACK = "icon_pack"
    const val BACK_GESTURE = "back_gesture"
    const val BATTERY_STYLE = "battery_style"
}
