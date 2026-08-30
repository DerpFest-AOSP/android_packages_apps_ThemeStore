/*
 * Copyright (C) 2025 AxionOS Project
 * Copyright (C) 2026 DerpFest AOSP
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
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import com.android.axion.axthemestore.R
import com.android.axion.axthemestore.data.model.StandardComponents
import com.android.axion.axthemestore.data.model.StoreSection
import com.android.axion.axthemestore.data.model.Theme
import com.android.axion.axthemestore.data.model.ThemeCategory
import com.android.axion.axthemestore.data.model.ThemeOverlay
import com.android.axion.axthemestore.data.model.ThemesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Offline catalog from [R.raw.overlay_icon_catalog], produced at build time from
 * `vendor/overlay` (Icons + themes/battery) and ThemePicker icon-pack RROs
 * (see [tools/generate_overlay_icon_catalog.py]). Does not enumerate installed APKs
 * or use the network.
 *
 * Extra RROs (battery / back gesture) are merged per [StoreSection.customizationOmsCategoriesToDiscover].
 */
class ThemeRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "ThemeRepository"
        private const val CACHE_DURATION_MS = 30_000L

        const val CATEGORY_WIFI = "android.theme.customization.wifi_icon"
        const val CATEGORY_SIGNAL = "android.theme.customization.signal_icon"

        const val ID_CATEGORY_WIFI = "wifi_icons"
        const val ID_CATEGORY_SIGNAL = "signal_icons"
        const val ID_CATEGORY_ICON_PACK = StandardComponents.ICON_PACK
        const val ID_CATEGORY_BATTERY = StandardComponents.BATTERY_STYLE

        /** Bundled / local RRO packages omitted from the store UI (ROM may still ship them). */
        private val HIDDEN_FROM_STORE_PACKAGES = setOf(
            "com.android.systemui.battery.faintui",
        )

        private val ICON_PACK_OVERLAY_ORDER = listOf(
            "android.theme.customization.icon_pack.android",
            "android.theme.customization.icon_pack.systemui",
            "android.theme.customization.icon_pack.settings",
            "android.theme.customization.icon_pack.launcher",
            "android.theme.customization.icon_pack.themepicker",
        )
    }
    
    private var cachedResponse: ThemesResponse? = null
    private var cachedSection: StoreSection? = null
    private var lastFetchTime: Long = 0

    suspend fun fetchThemes(
        forceRefresh: Boolean = false,
        section: StoreSection = StoreSection.All,
    ): Result<ThemesResponse> {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedResponse != null && cachedSection == section &&
                (now - lastFetchTime) < CACHE_DURATION_MS) {
                return@withContext Result.success(cachedResponse!!)
            }

            try {
                val text = context.resources.openRawResource(R.raw.overlay_icon_catalog)
                    .bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val entries = json.getJSONArray("entries")
                val pm = context.packageManager

                val catalogThemes = mutableListOf<Theme>()
                val iconPackRows = mutableListOf<IconPackCatalogRow>()

                for (i in 0 until entries.length()) {
                    val o = entries.getJSONObject(i)
                    val packageName = o.getString("package")
                    val label = o.getString("label")
                    val kind = o.getString("kind")
                    val customizationKey = o.getString("customizationKey")

                    if (packageName in HIDDEN_FROM_STORE_PACKAGES) continue

                    if (kind == "iconpack") {
                        iconPackRows.add(
                            IconPackCatalogRow(packageName, label, customizationKey)
                        )
                        continue
                    }

                    val categoryId = when (kind) {
                        "wifi" -> ID_CATEGORY_WIFI
                        "signal" -> ID_CATEGORY_SIGNAL
                        "battery" -> ID_CATEGORY_BATTERY
                        else -> continue
                    }

                    catalogThemes.add(
                        bundledSingleOverlayTheme(
                            categoryId = categoryId,
                            kind = kind,
                            label = label,
                            packageName = packageName,
                            customizationKey = customizationKey,
                            versionCode = installedVersionCode(pm, packageName),
                        )
                    )
                }

                catalogThemes.addAll(groupIconPackThemes(pm, iconPackRows))

                val mergedDiscovered = section.customizationOmsCategoriesToDiscover()?.let { allowed ->
                    if (allowed.isEmpty()) {
                        emptyList()
                    } else {
                        val discovered = discoverInstalledRroThemes(pm, allowed)
                        val catalogPackages =
                            catalogThemes.flatMap { it.overlays.map { overlay -> overlay.packageName } }.toSet()
                        discovered.filter { theme ->
                            val pkg = theme.overlays.firstOrNull()?.packageName
                            pkg != null &&
                                pkg !in catalogPackages &&
                                pkg !in HIDDEN_FROM_STORE_PACKAGES
                        }
                    }
                } ?: emptyList()

                val hasIconPacks = catalogThemes.any { it.category == ID_CATEGORY_ICON_PACK }
                val hasBattery = catalogThemes.any { it.category == ID_CATEGORY_BATTERY }

                val baseCategories = buildList {
                    add(
                        ThemeCategory(
                            id = ID_CATEGORY_WIFI,
                            name = context.getString(R.string.section_wifi_icons),
                            icon = "wifi"
                        )
                    )
                    add(
                        ThemeCategory(
                            id = ID_CATEGORY_SIGNAL,
                            name = context.getString(R.string.section_signal_icons),
                            icon = "signal_cellular_alt"
                        )
                    )
                    if (hasIconPacks) {
                        add(
                            ThemeCategory(
                                id = ID_CATEGORY_ICON_PACK,
                                name = context.getString(R.string.section_icon_packs),
                                icon = "apps"
                            )
                        )
                    }
                    if (hasBattery) {
                        add(
                            ThemeCategory(
                                id = ID_CATEGORY_BATTERY,
                                name = context.getString(R.string.section_battery_styles),
                                icon = "battery_full"
                            )
                        )
                    }
                }

                val knownBaseIds = baseCategories.map { it.id }.toSet()
                val extraCategories = mergedDiscovered
                    .map { it.category }
                    .distinct()
                    .filter { it !in knownBaseIds }
                    .map { catId ->
                        ThemeCategory(
                            id = catId,
                            name = formatDiscoveredCategoryTitle(catId),
                            icon = "palette"
                        )
                    }

                val networkCatalog = catalogThemes.filter {
                    it.category == ID_CATEGORY_WIFI || it.category == ID_CATEGORY_SIGNAL
                }
                val iconPackCatalog = catalogThemes.filter { it.category == ID_CATEGORY_ICON_PACK }
                val batteryCatalog = catalogThemes.filter { it.category == ID_CATEGORY_BATTERY }

                val (themes, categories) = when (section) {
                    StoreSection.All -> {
                        val combined = catalogThemes + mergedDiscovered
                        Pair(combined, baseCategories + extraCategories)
                    }
                    StoreSection.NetworkIcons -> Pair(
                        networkCatalog,
                        baseCategories.filter {
                            it.id == ID_CATEGORY_WIFI || it.id == ID_CATEGORY_SIGNAL
                        },
                    )
                    StoreSection.IconPacks -> Pair(
                        iconPackCatalog,
                        listOf(
                            ThemeCategory(
                                id = ID_CATEGORY_ICON_PACK,
                                name = context.getString(R.string.section_icon_packs),
                                icon = "apps",
                            )
                        ),
                    )
                    StoreSection.BatteryStyles -> Pair(
                        batteryCatalog + mergedDiscovered,
                        buildList {
                            add(
                                ThemeCategory(
                                    id = ID_CATEGORY_BATTERY,
                                    name = context.getString(R.string.section_battery_styles),
                                    icon = "battery_full",
                                )
                            )
                            addAll(extraCategories)
                        },
                    )
                    StoreSection.BackGesture,
                    StoreSection.StatusBarCustomization,
                    -> Pair(mergedDiscovered, extraCategories)
                }

                val response = ThemesResponse(
                    version = json.optInt("version", 1),
                    lastUpdated = "",
                    themes = themes,
                    categories = categories,
                    components = emptyList()
                )
                cachedResponse = response
                cachedSection = section
                lastFetchTime = now

                Log.d(
                    TAG,
                    "Loaded store section=$section: ${themes.size} themes, ${categories.size} categories"
                )

                Result.success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load overlay_icon_catalog", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getThemes(
        forceRefresh: Boolean = false,
        section: StoreSection = StoreSection.All,
    ): Result<List<Theme>> {
        return fetchThemes(forceRefresh, section).map { it.themes }
    }

    suspend fun getCategories(
        forceRefresh: Boolean = false,
        section: StoreSection = StoreSection.All,
    ): Result<List<ThemeCategory>> {
        return fetchThemes(forceRefresh, section).map { it.categories }
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
        cachedSection = null
        lastFetchTime = 0
    }

    private data class IconPackCatalogRow(
        val packageName: String,
        val label: String,
        val customizationKey: String,
    )

    private fun groupIconPackThemes(
        pm: PackageManager,
        rows: List<IconPackCatalogRow>,
    ): List<Theme> {
        if (rows.isEmpty()) return emptyList()

        val grouped = linkedMapOf<String, MutableList<IconPackCatalogRow>>()
        for (row in rows) {
            val style = iconPackStyleKey(row.packageName)
            grouped.getOrPut(style) { mutableListOf() }.add(row)
        }

        return grouped.map { (style, styleRows) ->
            val sortedRows = styleRows.sortedBy { row ->
                val idx = ICON_PACK_OVERLAY_ORDER.indexOf(row.customizationKey)
                if (idx >= 0) idx else ICON_PACK_OVERLAY_ORDER.size
            }
            val displayName = sortedRows
                .firstOrNull { it.customizationKey.endsWith(".android") }
                ?.label
                ?: sortedRows.first().label
            val overlays = sortedRows.map { row ->
                ThemeOverlay(
                    componentId = row.customizationKey,
                    packageName = row.packageName,
                    targetPackage = "",
                    targets = emptyList(),
                    downloadUrl = "",
                    fileSize = 0L,
                    enabled = true
                )
            }
            Theme(
                id = "${ID_CATEGORY_ICON_PACK}_$style",
                name = displayName,
                description = "",
                author = context.getString(R.string.bundled_overlay_author),
                version = "1.0",
                versionCode = installedVersionCode(pm, overlays.first().packageName),
                minSdk = 31,
                previewImages = emptyList(),
                category = ID_CATEGORY_ICON_PACK,
                tags = listOf("iconpack"),
                overlays = overlays,
                isUnified = false,
                isBundledOverlay = true,
                supportsRegionSampling = false,
            )
        }
    }

    private fun bundledSingleOverlayTheme(
        categoryId: String,
        kind: String,
        label: String,
        packageName: String,
        customizationKey: String,
        versionCode: Int,
    ): Theme {
        return Theme(
            id = "${categoryId}_${packageName.replace('.', '_')}",
            name = label,
            description = "",
            author = context.getString(R.string.bundled_overlay_author),
            version = "1.0",
            versionCode = versionCode,
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
            isBundledOverlay = true,
            supportsRegionSampling = false,
        )
    }

    private fun installedVersionCode(pm: PackageManager, packageName: String): Int {
        return try {
            pm.getPackageInfo(packageName, 0).longVersionCode.toInt()
        } catch (_: PackageManager.NameNotFoundException) {
            1
        }
    }

    private fun discoverInstalledRroThemes(
        pm: PackageManager,
        allowedOmsCategories: Set<String>,
    ): List<Theme> {
        val themes = mutableListOf<Theme>()
        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        for (packageInfo in packages) {
            if (packageInfo.applicationInfo?.enabled == false) continue
            if (!packageInfo.hasOverlayTarget()) continue
            val overlayCategory = packageInfo.overlayCategory ?: continue
            if (overlayCategory !in allowedOmsCategories) continue

            val appInfo = packageInfo.applicationInfo ?: continue
            val appLabel = pm.getApplicationLabel(appInfo).toString()
            val packageName = packageInfo.packageName
            val componentId = overlayCategory.removePrefix("android.theme.customization.")
            val uiCategory = componentId

            themes.add(
                Theme(
                    id = "local_$packageName",
                    name = appLabel,
                    description = context.getString(R.string.local_rro_theme_description),
                    author = context.getString(R.string.third_party_author),
                    version = packageInfo.versionName ?: "1.0",
                    versionCode = packageInfo.longVersionCode.toInt(),
                    minSdk = appInfo.minSdkVersion,
                    previewImages = emptyList(),
                    category = uiCategory,
                    tags = listOf(uiCategory, "local"),
                    overlays = listOf(
                        ThemeOverlay(
                            componentId = componentId,
                            packageName = packageName,
                            targetPackage = packageInfo.overlayTarget ?: "",
                            targets = listOf(overlayCategory),
                            downloadUrl = "",
                            fileSize = 0L,
                            enabled = true
                        )
                    ),
                    isUnified = true,
                    isBundledOverlay = false,
                    supportsRegionSampling = false,
                )
            )
        }
        return themes
    }
}

private fun iconPackStyleKey(packageName: String): String {
    val prefix = "com.android.theme.icon_pack."
    if (packageName.startsWith(prefix)) {
        val rest = packageName.removePrefix(prefix)
        val dot = rest.lastIndexOf('.')
        if (dot > 0) return rest.substring(0, dot)
    }
    return packageName.substringAfterLast('.')
}

private fun PackageInfo.hasOverlayTarget(): Boolean {
    return overlayTarget?.isNotEmpty() == true
}

private fun formatDiscoveredCategoryTitle(categoryId: String): String {
    return categoryId
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecaseChar() }
        }
}
