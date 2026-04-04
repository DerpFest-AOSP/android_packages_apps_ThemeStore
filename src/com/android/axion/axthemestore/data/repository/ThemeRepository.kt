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

package com.android.axion.axthemestore.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.android.axion.axthemestore.R
import com.android.axion.axthemestore.data.model.Theme
import com.android.axion.axthemestore.data.model.ThemeCategory
import com.android.axion.axthemestore.data.model.ThemeOverlay
import com.android.axion.axthemestore.data.model.ThemesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Offline catalog from [R.raw.overlay_icon_catalog], produced at build time from
 * `vendor/overlay/Icons` (see [tools/generate_overlay_icon_catalog.py]). Does not enumerate
 * installed APKs or use the network.
 */
class ThemeRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "ThemeRepository"
        private const val CACHE_DURATION_MS = 30_000L

        const val CATEGORY_WIFI = "android.theme.customization.wifi_icon"
        const val CATEGORY_SIGNAL = "android.theme.customization.signal_icon"
        /** Status bar mobile data type indicators (LTE, 5G, etc.) — OMS overlay category. */
        const val CATEGORY_DATA = "android.customization.sb_data"

        const val ID_CATEGORY_WIFI = "wifi_icons"
        const val ID_CATEGORY_SIGNAL = "signal_icons"
        const val ID_CATEGORY_DATA = "data_icons"
    }
    
    private var cachedResponse: ThemesResponse? = null
    private var lastFetchTime: Long = 0
    
    suspend fun fetchThemes(forceRefresh: Boolean = false): Result<ThemesResponse> {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedResponse != null &&
                (now - lastFetchTime) < CACHE_DURATION_MS) {
                return@withContext Result.success(cachedResponse!!)
            }
            
            try {
                val text = context.resources.openRawResource(R.raw.overlay_icon_catalog)
                    .bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val entries = json.getJSONArray("entries")
                val pm = context.packageManager
                
                val themes = mutableListOf<Theme>()
                
                for (i in 0 until entries.length()) {
                    val o = entries.getJSONObject(i)
                    val packageName = o.getString("package")
                    val label = o.getString("label")
                    val kind = o.getString("kind")
                    val customizationKey = o.getString("customizationKey")
                    
                    val categoryId = when (kind) {
                        "wifi" -> ID_CATEGORY_WIFI
                        "signal" -> ID_CATEGORY_SIGNAL
                        "data" -> ID_CATEGORY_DATA
                        else -> continue
                    }
                    
                    val vc = try {
                        pm.getPackageInfo(packageName, 0).longVersionCode.toInt()
                    } catch (_: PackageManager.NameNotFoundException) {
                        1
                    }
                    
                    themes.add(
                        Theme(
                            id = "${categoryId}_${packageName.replace('.', '_')}",
                            name = label,
                            // Long copy lives in string resources for detail screen only (see ThemeDetailScreen).
                            description = "",
                            author = context.getString(R.string.bundled_overlay_author),
                            version = "1.0",
                            versionCode = vc,
                            minSdk = 31,
                            previewImages = emptyList(),
                            category = categoryId,
                            tags = listOf(kind),
                            overlays = listOf(
                                ThemeOverlay(
                                    componentId = customizationKey,
                                    packageName = packageName,
                                    targetPackage = "",
                                    targets = emptyList(),
                                    downloadUrl = "",
                                    fileSize = 0L,
                                    enabled = true
                                )
                            ),
                            isUnified = false,
                            isBundledOverlay = true
                        )
                    )
                }
                
                val categories = listOf(
                    ThemeCategory(
                        id = ID_CATEGORY_WIFI,
                        name = context.getString(R.string.section_wifi_icons),
                        icon = "wifi"
                    ),
                    ThemeCategory(
                        id = ID_CATEGORY_SIGNAL,
                        name = context.getString(R.string.section_signal_icons),
                        icon = "signal_cellular_alt"
                    ),
                    ThemeCategory(
                        id = ID_CATEGORY_DATA,
                        name = context.getString(R.string.section_data_icons),
                        icon = "data_usage"
                    )
                )
                
                val response = ThemesResponse(
                    version = json.optInt("version", 1),
                    lastUpdated = "",
                    themes = themes,
                    categories = categories,
                    components = emptyList()
                )
                cachedResponse = response
                lastFetchTime = now
                
                Log.d(TAG, "Loaded ${themes.size} offline overlay entries from raw catalog")
                
                Result.success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load overlay_icon_catalog", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun getThemes(forceRefresh: Boolean = false): Result<List<Theme>> {
        return fetchThemes(forceRefresh).map { it.themes }
    }
    
    suspend fun getCategories(forceRefresh: Boolean = false): Result<List<ThemeCategory>> {
        return fetchThemes(forceRefresh).map { it.categories }
    }
    
    suspend fun getThemesByCategory(
        categoryId: String,
        forceRefresh: Boolean = false
    ): Result<List<Theme>> {
        return getThemes(forceRefresh).map { themes ->
            themes.filter { it.category == categoryId }
        }
    }
    
    suspend fun searchThemes(
        query: String,
        forceRefresh: Boolean = false
    ): Result<List<Theme>> {
        return getThemes(forceRefresh).map { themes ->
            val lowerQuery = query.lowercase()
            themes.filter { theme ->
                theme.name.lowercase().contains(lowerQuery) ||
                    theme.description.lowercase().contains(lowerQuery) ||
                    theme.author.lowercase().contains(lowerQuery) ||
                    theme.tags.any { it.lowercase().contains(lowerQuery) }
            }
        }
    }
    
    fun getInstalledVersionCode(packageName: String): Int? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.longVersionCode.toInt()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    fun isThemeInstalled(packageName: String): Boolean {
        return getInstalledVersionCode(packageName) != null
    }
    
    fun clearCache() {
        cachedResponse = null
        lastFetchTime = 0
    }
}
