/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.axion.axthemestore.data.model

import android.content.Intent

/**
 * Which part of the store to show. Launch [com.android.axion.axthemestore.MainActivity] with
 * [EXTRA_STORE_SECTION] so Settings / SystemUI can open a dedicated screen (network icons, battery
 * styles only, etc.).
 */
enum class StoreSection {
    /** Wi‑Fi and signal icon packs from [R.raw.overlay_icon_catalog]. */
    NetworkIcons,

    /** ThemePicker system icon pack RROs. */
    IconPacks,

    /** Battery shape RROs only. */
    BatteryStyles,

    /** Back gesture appearance RROs only. */
    BackGesture,

    /**
     * Battery + back gesture in one list (legacy combined shortcut).
     * Prefer [BatteryStyles] or [BackGesture] for separate Settings entries.
     */
    StatusBarCustomization,

    /** Full store: network catalog plus all supported customization RROs. */
    All,
    ;

    /**
     * Full OMS `android:category` strings to discover for this section.
     * `null` means skip customization discovery entirely.
     */
    fun customizationOmsCategoriesToDiscover(): Set<String>? = when (this) {
        NetworkIcons, IconPacks -> null
        All, StatusBarCustomization -> CustomizationOmsAll
        BatteryStyles -> setOf(Oms.BATTERY_STYLE)
        BackGesture -> setOf(Oms.BACK_GESTURE)
    }

    fun isRelevantCategoryKey(key: String): Boolean = when (this) {
        All -> true
        NetworkIcons -> key in NetworkCategoryKeys
        IconPacks -> isIconPackCategoryKey(key)
        BatteryStyles -> key in BatteryCategoryKeys
        BackGesture -> key in BackGestureCategoryKeys
        StatusBarCustomization -> key in CustomizationCategoryKeys
    }

    companion object {
        const val EXTRA_STORE_SECTION = "com.android.axion.axthemestore.extra.STORE_SECTION"

        const val VALUE_NETWORK_ICONS = "network_icons"

        const val VALUE_ICON_PACKS = "icon_packs"

        /** Launch battery-style overlays only. */
        const val VALUE_BATTERY_STYLE = "battery_style"

        /** Launch back-gesture overlays only. */
        const val VALUE_BACK_GESTURE = "back_gesture"

        /** Legacy: battery style and back gesture in one activity. */
        const val VALUE_STATUS_BAR_CUSTOMIZATION = "status_bar_customization"

        const val VALUE_ALL = "all"

        fun fromIntent(intent: Intent?): StoreSection {
            val v = intent?.getStringExtra(EXTRA_STORE_SECTION)?.lowercase() ?: return All
            return when (v) {
                VALUE_NETWORK_ICONS -> NetworkIcons
                VALUE_ICON_PACKS -> IconPacks
                VALUE_BATTERY_STYLE -> BatteryStyles
                VALUE_BACK_GESTURE -> BackGesture
                VALUE_STATUS_BAR_CUSTOMIZATION -> StatusBarCustomization
                VALUE_ALL -> All
                else -> All
            }
        }

        private object Oms {
            const val BATTERY_STYLE = "android.theme.customization.battery_style"
            const val BACK_GESTURE = "android.theme.customization.back_gesture"
        }

        private val CustomizationOmsAll = setOf(
            Oms.BATTERY_STYLE,
            Oms.BACK_GESTURE,
        )

        private val NetworkCategoryKeys = setOf(
            "android.theme.customization.wifi_icon",
            "android.theme.customization.signal_icon",
            "wifi",
            "signal",
            "statusbar_wifi",
            "statusbar_signal",
        )

        private val BatteryCategoryKeys = setOf(
            Oms.BATTERY_STYLE,
            "battery_style",
        )

        private val BackGestureCategoryKeys = setOf(
            Oms.BACK_GESTURE,
            "back_gesture",
        )

        private val CustomizationCategoryKeys = BatteryCategoryKeys +
            BackGestureCategoryKeys

        private fun isIconPackCategoryKey(key: String): Boolean {
            return key == StandardComponents.ICON_PACK ||
                key.startsWith("android.theme.customization.icon_pack.")
        }
    }
}
