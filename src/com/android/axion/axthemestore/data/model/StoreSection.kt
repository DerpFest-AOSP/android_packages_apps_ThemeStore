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

import android.content.Intent

/**
 * Which part of the store to show. Launch [com.android.axion.axthemestore.MainActivity] with
 * [EXTRA_STORE_SECTION] so Settings / SystemUI can open a dedicated screen (network icons, battery
 * styles only, etc.).
 */
enum class StoreSection {
    /** Wi‑Fi, signal, and mobile data icon packs from [R.raw.overlay_icon_catalog]. */
    NetworkIcons,

    /** Battery shape RROs only. */
    BatteryStyles,

    /** Back gesture appearance RROs only. */
    BackGesture,

    /** Charging animation RROs only. */
    ChargingAnimation,

    /**
     * Battery + charging + back gesture in one list (legacy combined shortcut).
     * Prefer [BatteryStyles], [BackGesture], [ChargingAnimation] for separate Settings entries.
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
        NetworkIcons -> null
        All, StatusBarCustomization -> CustomizationOmsAll
        BatteryStyles -> setOf(Oms.BATTERY_STYLE)
        BackGesture -> setOf(Oms.BACK_GESTURE)
        ChargingAnimation -> setOf(Oms.CHARGING_ANIMATION)
    }

    fun isRelevantCategoryKey(key: String): Boolean = when (this) {
        All -> true
        NetworkIcons -> key in NetworkCategoryKeys
        BatteryStyles -> key in BatteryCategoryKeys
        BackGesture -> key in BackGestureCategoryKeys
        ChargingAnimation -> key in ChargingAnimationCategoryKeys
        StatusBarCustomization -> key in CustomizationCategoryKeys
    }

    companion object {
        const val EXTRA_STORE_SECTION = "com.android.axion.axthemestore.extra.STORE_SECTION"

        const val VALUE_NETWORK_ICONS = "network_icons"

        /** Launch battery-style overlays only. */
        const val VALUE_BATTERY_STYLE = "battery_style"

        /** Launch back-gesture overlays only. */
        const val VALUE_BACK_GESTURE = "back_gesture"

        /** Launch charging-animation overlays only. */
        const val VALUE_CHARGING_ANIMATION = "charging_animation"

        /** Legacy: all three customization types in one activity. */
        const val VALUE_STATUS_BAR_CUSTOMIZATION = "status_bar_customization"

        const val VALUE_ALL = "all"

        fun fromIntent(intent: Intent?): StoreSection {
            val v = intent?.getStringExtra(EXTRA_STORE_SECTION)?.lowercase() ?: return All
            return when (v) {
                VALUE_NETWORK_ICONS -> NetworkIcons
                VALUE_BATTERY_STYLE -> BatteryStyles
                VALUE_BACK_GESTURE -> BackGesture
                VALUE_CHARGING_ANIMATION -> ChargingAnimation
                VALUE_STATUS_BAR_CUSTOMIZATION -> StatusBarCustomization
                VALUE_ALL -> All
                else -> All
            }
        }

        private object Oms {
            const val BATTERY_STYLE = "android.theme.customization.battery_style"
            const val CHARGING_ANIMATION = "android.theme.customization.charging_animation"
            const val BACK_GESTURE = "android.theme.customization.back_gesture"
        }

        private val CustomizationOmsAll = setOf(
            Oms.BATTERY_STYLE,
            Oms.CHARGING_ANIMATION,
            Oms.BACK_GESTURE,
        )

        private val NetworkCategoryKeys = setOf(
            "android.theme.customization.wifi_icon",
            "android.theme.customization.signal_icon",
            "android.customization.sb_data",
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

        private val ChargingAnimationCategoryKeys = setOf(
            Oms.CHARGING_ANIMATION,
            "charging_animation",
        )

        private val CustomizationCategoryKeys = BatteryCategoryKeys +
            BackGestureCategoryKeys +
            ChargingAnimationCategoryKeys
    }
}
