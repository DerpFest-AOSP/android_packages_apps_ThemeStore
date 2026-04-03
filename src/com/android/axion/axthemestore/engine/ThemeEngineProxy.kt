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

package com.android.axion.axthemestore.engine

import android.content.Context
import android.content.om.OverlayManager
import android.content.om.OverlayManagerTransaction
import android.content.res.ThemeEngine
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

class ThemeEngineProxy(private val context: Context) {

    companion object {
        private const val TAG = "ThemeEngineProxy"
        
        const val SETTINGS_THEME_ENGINE_DATA = "theme_engine_data"

        object Category {
            const val STATUSBAR_WIFI = "statusbar_wifi"
            const val STATUSBAR_SIGNAL = "statusbar_signal"
            
            const val ANDROID = "android"
            const val SYSTEMUI = "systemui"
            
            const val UI_QS = "ui_qs"
            const val UI_VOLUME = "ui_volume"
            const val UI_STYLE = "ui_style"
        }
        
        object UiStyle {
            const val AXION = "axion"
            const val MATERIAL3_EXPRESSIVE = "material3_expressive"
            const val MINIMAL = "minimal"
        }
    }
    
    fun getThemeConfig(): ThemeEngineConfig {
        return try {
            val json = Settings.Secure.getString(
                context.contentResolver,
                SETTINGS_THEME_ENGINE_DATA
            )
            if (json.isNullOrBlank()) {
                ThemeEngineConfig()
            } else {
                parseConfig(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse theme config", e)
            ThemeEngineConfig()
        }
    }
    
    private fun saveThemeConfig(config: ThemeEngineConfig): Boolean {
        return try {
            val json = serializeConfig(config)
            Settings.Secure.putString(
                context.contentResolver,
                SETTINGS_THEME_ENGINE_DATA,
                json
            )
            Log.d(TAG, "Saved theme config: $json")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save theme config", e)
            false
        }
    }

    private fun parseConfig(jsonStr: String): ThemeEngineConfig {
        return try {
            val json = JSONObject(jsonStr)
            val version = json.optInt("version", 1)
            val iconTheme = if (json.has("systemThemeIcons") && !json.isNull("systemThemeIcons")) 
                json.getString("systemThemeIcons") else null
            val iconThemeTargets = mutableListOf<String>()
            val targetsArr = json.optJSONArray("systemThemeIconTargets")
            if (targetsArr != null) {
                for (i in 0 until targetsArr.length()) {
                    iconThemeTargets.add(targetsArr.getString(i))
                }
            }
            
            val categoryThemes = mutableMapOf<String, String>()
            val catThemesObj = json.optJSONObject("categoryThemes")
            catThemesObj?.keys()?.forEach { key ->
                val pkgName = catThemesObj.optString(key)
                if (pkgName.isNotBlank()) {
                    categoryThemes[key] = pkgName
                }
            }
                
            val themesMap = mutableMapOf<String, ThemeCategoryConfig>()
            val themesObj = json.optJSONObject("themes")
            
            themesObj?.keys()?.forEach { key ->
                val catObj = themesObj.getJSONObject(key)
                val enabled = catObj.optBoolean("enabled", false)
                val pkgName = if (catObj.has("packageName") && !catObj.isNull("packageName")) 
                    catObj.getString("packageName") else null
                val styleId = if (catObj.has("styleId") && !catObj.isNull("styleId")) 
                    catObj.getString("styleId") else null
                    
                themesMap[key] = ThemeCategoryConfig(enabled, pkgName, styleId)
            }
            
            ThemeEngineConfig(version, themesMap, iconTheme, iconThemeTargets, categoryThemes)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config JSON", e)
            ThemeEngineConfig()
        }
    }
    
    private fun serializeConfig(config: ThemeEngineConfig): String {
        val json = JSONObject()
        json.put("version", config.version)
        json.put("systemThemeIcons", config.iconTheme)
        val targetsArr = org.json.JSONArray()
        config.iconThemeTargets.forEach { targetsArr.put(it) }
        json.put("systemThemeIconTargets", targetsArr)
        
        val catThemesObj = JSONObject()
        config.categoryThemes.forEach { (key, value) ->
            catThemesObj.put(key, value)
        }
        json.put("categoryThemes", catThemesObj)
        
        val themesObj = JSONObject()
        config.themes.forEach { (key, value) ->
            val catObj = JSONObject()
            catObj.put("enabled", value.enabled)
            catObj.put("packageName", value.packageName)
            catObj.put("styleId", value.styleId)
            themesObj.put(key, catObj)
        }
        json.put("themes", themesObj)
        
        return json.toString()
    }
    
    fun enableTheme(category: String, packageName: String): Boolean {
        val config = getThemeConfig()
        val updatedThemes = config.themes.toMutableMap()
        updatedThemes[category] = ThemeCategoryConfig(
            enabled = true,
            packageName = packageName
        )
        return saveThemeConfig(config.copy(themes = updatedThemes))
    }
    
    fun disableTheme(category: String): Boolean {
        val config = getThemeConfig()
        val updatedThemes = config.themes.toMutableMap()
        updatedThemes[category] = ThemeCategoryConfig(
            enabled = false,
            packageName = null
        )
        return saveThemeConfig(config.copy(themes = updatedThemes))
    }
    
    fun enableThemeOverlays(overlays: Map<String, String>): Boolean {
        val config = getThemeConfig()
        val updatedThemes = config.themes.toMutableMap()
        
        overlays.forEach { (category, packageName) ->
            updatedThemes[category] = ThemeCategoryConfig(
                enabled = true,
                packageName = packageName
            )
        }
        
        return saveThemeConfig(config.copy(themes = updatedThemes))
    }
    
    fun disableThemeOverlays(categories: List<String>): Boolean {
        val config = getThemeConfig()
        val updatedThemes = config.themes.toMutableMap()
        
        categories.forEach { category ->
            updatedThemes[category] = ThemeCategoryConfig(
                enabled = false,
                packageName = null
            )
        }
        
        return saveThemeConfig(config.copy(themes = updatedThemes))
    }
    
    fun isThemeEnabled(category: String): Boolean {
        return getThemeConfig().themes[category]?.enabled == true
    }
    
    fun getEnabledPackage(category: String): String? {
        val categoryConfig = getThemeConfig().themes[category]
        return if (categoryConfig?.enabled == true) categoryConfig.packageName else null
    }
    
    fun getEnabledThemes(): Map<String, String> {
        return getThemeConfig().themes
            .filter { it.value.enabled && it.value.packageName != null }
            .mapValues { it.value.packageName!! }
    }
    
    fun clearAllThemes(): Boolean {
        return saveThemeConfig(ThemeEngineConfig())
    }
    
    fun setUiStyle(styleId: String): Boolean {
        val config = getThemeConfig()
        val updatedThemes = config.themes.toMutableMap()
        updatedThemes[Category.UI_STYLE] = ThemeCategoryConfig(
            enabled = true,
            packageName = null,
            styleId = styleId
        )
        updatedThemes[Category.UI_QS] = ThemeCategoryConfig(
            enabled = true,
            packageName = null,
            styleId = styleId
        )
        return saveThemeConfig(config.copy(themes = updatedThemes))
    }
    
    fun getUiStyle(): String {
        return getThemeConfig().themes[Category.UI_QS]?.styleId
            ?: getThemeConfig().themes[Category.UI_STYLE]?.styleId
            ?: UiStyle.AXION
    }
    
    fun setIconTheme(packageName: String): Boolean {
        val config = getThemeConfig()
        return saveThemeConfig(config.copy(iconTheme = packageName))
    }
    
    fun getIconTheme(): String? {
        return getThemeConfig().iconTheme
    }
    
    fun clearIconTheme(): Boolean {
        val config = getThemeConfig()
        return saveThemeConfig(config.copy(iconTheme = null, iconThemeTargets = emptyList()))
    }
    
    fun clearCategoryThemesForPackage(packageName: String): Boolean {
        val config = getThemeConfig()
        val updatedCategoryThemes = config.categoryThemes.filterValues { it != packageName }

        val allTargets = updatedCategoryThemes.keys.toList()
        val uniquePackages = updatedCategoryThemes.values.toSet()
        val iconThemePackage = if (uniquePackages.size == 1) uniquePackages.first() else null

        val saved = saveThemeConfig(config.copy(
            iconTheme = iconThemePackage,
            iconThemeTargets = allTargets,
            categoryThemes = updatedCategoryThemes
        ))
        return saved
    }
    
    fun getIconThemeTargets(): List<String> {
        return getThemeConfig().iconThemeTargets
    }
    
    fun setIconThemeWithTargets(packageName: String, targets: List<String>): Boolean {
        val config = getThemeConfig()
        val updatedCategoryThemes = config.categoryThemes.toMutableMap()
        targets.forEach { target ->
            updatedCategoryThemes[target] = packageName
        }
        val saved = saveThemeConfig(config.copy(
            iconTheme = packageName,
            iconThemeTargets = targets,
            categoryThemes = updatedCategoryThemes
        ))
        return saved
    }
    
    fun enableIconThemeTarget(target: String): Boolean {
        val config = getThemeConfig()
        if (config.iconTheme == null) return false
        
        val newTargets = config.iconThemeTargets.toMutableList()
        if (!newTargets.contains(target)) {
            newTargets.add(target)
        }
        return saveThemeConfig(config.copy(iconThemeTargets = newTargets))
    }
    
    fun disableIconThemeTarget(target: String): Boolean {
        val config = getThemeConfig()
        if (config.iconTheme == null) return false
        
        val newTargets = config.iconThemeTargets.toMutableList()
        newTargets.remove(target)
        return saveThemeConfig(config.copy(iconThemeTargets = newTargets))
    }
    
    fun isIconThemeTargetEnabled(target: String): Boolean {
        val config = getThemeConfig()
        return config.iconTheme != null && config.iconThemeTargets.contains(target)
    }
    
    fun setCategoryTheme(category: String, packageName: String): Boolean {
        val config = getThemeConfig()
        val oldPackage = config.categoryThemes[category]
        val updatedCategoryThemes = config.categoryThemes.toMutableMap()
        updatedCategoryThemes[category] = packageName

        val allTargets = updatedCategoryThemes.keys.toList()
        val uniquePackages = updatedCategoryThemes.values.toSet()
        val iconThemePackage = if (uniquePackages.size == 1) uniquePackages.first() else null

        val saved = saveThemeConfig(config.copy(
            iconTheme = iconThemePackage,
            iconThemeTargets = allTargets,
            categoryThemes = updatedCategoryThemes
        ))
        if (saved) {
            setOverlayEnabled(packageName, true, oldPackage)
        }
        return saved
    }

    fun clearCategoryTheme(category: String): Boolean {
        val config = getThemeConfig()
        val oldPackage = config.categoryThemes[category]
        val updatedCategoryThemes = config.categoryThemes.toMutableMap()
        updatedCategoryThemes.remove(category)

        val allTargets = updatedCategoryThemes.keys.toList()
        val uniquePackages = updatedCategoryThemes.values.toSet()
        val iconThemePackage = if (uniquePackages.size == 1) uniquePackages.first() else null

        val saved = saveThemeConfig(config.copy(
            iconTheme = iconThemePackage,
            iconThemeTargets = allTargets,
            categoryThemes = updatedCategoryThemes
        ))
        if (saved) {
            if (oldPackage != null) {
                setOverlayEnabled(oldPackage, false, null)
            }
        }
        return saved
    }

    private fun setOverlayEnabled(packageName: String, enabled: Boolean, disablePackage: String?) {
        try {
            val om = context.getSystemService(OverlayManager::class.java) ?: return
            val userHandle = UserHandle.of(UserHandle.myUserId())
            val builder = OverlayManagerTransaction.Builder()
            if (disablePackage != null && disablePackage != packageName) {
                try {
                    val info = om.getOverlayInfo(disablePackage, userHandle)
                    if (info != null) {
                        builder.setEnabled(info.overlayIdentifier, false)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to disable old overlay: $disablePackage", e)
                }
            }
            try {
                val info = om.getOverlayInfo(packageName, userHandle)
                if (info != null) {
                    builder.setEnabled(info.overlayIdentifier, enabled)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ${if (enabled) "enable" else "disable"} overlay: $packageName", e)
            }
            om.commit(builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to manage overlay: $packageName", e)
        }
    }
    
    fun getCategoryTheme(category: String): String? {
        return getThemeConfig().categoryThemes[category]
    }
    
    fun getCategoryThemes(): Map<String, String> {
        return getThemeConfig().categoryThemes
    }
    
    fun applyThemeComponents(packageName: String, categories: List<String>): Boolean {
        val config = getThemeConfig()
        val updatedCategoryThemes = config.categoryThemes.toMutableMap()
        
        val categoriesToRemove = updatedCategoryThemes.entries
            .filter { it.value == packageName && it.key !in categories }
            .map { it.key }
        
        categoriesToRemove.forEach { category ->
            updatedCategoryThemes.remove(category)
        }
        
        categories.forEach { category ->
            updatedCategoryThemes[category] = packageName
        }
        
        val allTargets = updatedCategoryThemes.keys.toList()
        
        val uniquePackages = updatedCategoryThemes.values.toSet()
        val iconThemePackage = if (uniquePackages.size == 1) uniquePackages.first() else null
        
        val saved = saveThemeConfig(config.copy(
            iconTheme = iconThemePackage,
            iconThemeTargets = allTargets,
            categoryThemes = updatedCategoryThemes
        ))
        return saved
    }

    private fun getThemeEngine(): ThemeEngine? = ThemeEngine.getInstance(context)

    fun notifyThemeChanged() {
        val engine = getThemeEngine() ?: return
        try {
            engine.notifyThemeChanged(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify theme changed", e)
        }
    }
}

data class ThemeEngineConfig(
    val version: Int = 1,
    val themes: Map<String, ThemeCategoryConfig> = emptyMap(),
    val iconTheme: String? = null,
    val iconThemeTargets: List<String> = emptyList(),
    val categoryThemes: Map<String, String> = emptyMap(),
)

data class ThemeCategoryConfig(
    val enabled: Boolean = false,
    val packageName: String? = null,
    val styleId: String? = null
)
