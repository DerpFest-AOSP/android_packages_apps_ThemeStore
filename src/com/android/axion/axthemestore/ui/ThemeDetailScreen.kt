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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.axion.axthemestore.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.axion.axthemestore.R
import com.android.axion.axthemestore.data.model.Theme
import com.android.axion.axthemestore.data.model.ThemeOverlay
import com.android.axion.axthemestore.data.model.ThemeSelectionState
import com.android.axion.axthemestore.data.model.formatFileSize
import com.android.axion.axthemestore.data.repository.ThemeRepository
import com.android.axion.axthemestore.ui.components.BackGesturePreview
import com.android.axion.axthemestore.ui.components.BatteryStylePreview
import com.android.axion.axthemestore.ui.components.ThemePackagePreview
import com.android.axion.axthemestore.viewmodel.ThemeStoreViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThemeDetailScreen(
    theme: Theme,
    viewModel: ThemeStoreViewModel,
    onBackClick: () -> Unit
) {
    val themeStates by viewModel.themeStates.collectAsStateWithLifecycle()
    val selectionState = themeStates[theme.id] ?: ThemeSelectionState.Inactive
    val pendingChanges by viewModel.pendingComponentChanges.collectAsStateWithLifecycle()
    val hasPendingChanges = pendingChanges.containsKey(theme.id)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(theme.name) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 1.dp
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    ApplySection(
                        theme = theme,
                        selectionState = selectionState,
                        hasPendingChanges = hasPendingChanges,
                        onApplyClick = { viewModel.applyTheme(theme) },
                        onApplyPendingClick = { viewModel.applyPendingChanges(theme) },
                        onDisableClick = { viewModel.disableTheme(theme) },
                        onClearError = { viewModel.clearError(theme.id) }
                    )
                }
            }
        }
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                DetailPreviewBox(theme = theme)
            }
            
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "by ${theme.author}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                val detailDescription = theme.description.ifBlank {
                    when (theme.category) {
                        ThemeRepository.ID_CATEGORY_WIFI -> stringResource(R.string.wifi_icons_desc)
                        ThemeRepository.ID_CATEGORY_SIGNAL -> stringResource(R.string.signal_icons_desc)
                        ThemeRepository.ID_CATEGORY_DATA -> stringResource(R.string.data_icons_desc)
                        ThemeRepository.ID_CATEGORY_BATTERY -> stringResource(R.string.battery_styles_desc)
                        else -> ""
                    }
                }
                if (detailDescription.isNotBlank()) {
                    Text(
                        text = detailDescription,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                if (theme.isUnified && theme.overlays.isNotEmpty()) {
                    val overlay = theme.overlays.first()
                    val packageName = overlay.packageName
                    val isPackageOnDevice = selectionState is ThemeSelectionState.Active ||
                        selectionState is ThemeSelectionState.Inactive
                    
                    val enabledComponents by viewModel.enabledComponents.collectAsStateWithLifecycle()
                    val categoryThemes by viewModel.categoryThemesState.collectAsStateWithLifecycle()
                    val pendingChanges by viewModel.pendingComponentChanges.collectAsStateWithLifecycle()
                    val hasPending = pendingChanges.containsKey(theme.id)
                    
                    if (overlay.targets.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.components),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (hasPending) {
                                Text(
                                    text = stringResource(R.string.pending_changes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        overlay.targets.forEach { target ->
                            val isFromThisTheme = categoryThemes[target] == packageName
                            val currentThemePkg = categoryThemes[target]
                            
                            val pendingSet = pendingChanges[theme.id]
                            val isEnabled = when {
                                pendingSet != null -> pendingSet.contains(target)
                                isFromThisTheme -> true
                                else -> false
                            }
                            
                            ComponentSelectionItem(
                                componentId = target,
                                isEnabled = isEnabled,
                                isToggleable = isPackageOnDevice,
                                currentSource = if (!isFromThisTheme && currentThemePkg != null) {
                                    currentThemePkg.substringAfterLast('.')
                                } else null,
                                onToggle = { enabled ->
                                    if (isPackageOnDevice) {
                                        viewModel.toggleComponent(theme, target, enabled)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                } else if (theme.overlays.isNotEmpty() && !theme.isUnified) {
                    Text(
                        text = stringResource(R.string.components),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    theme.overlays.forEach { overlay ->
                        ComponentOverlayItem(overlay = overlay)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (theme.tags.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.tags),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(theme.tags.size) { index ->
                            TagChip(tag = theme.tags[index])
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
            }
        }
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TagChip(tag: String) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ComponentOverlayItem(overlay: ThemeOverlay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = overlay.componentId.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = overlay.targetPackage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = overlay.fileSize.formatFileSize(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UnifiedComponentItem(
    componentId: String,
    isEnabled: Boolean,
    isToggleable: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = getComponentDisplayName(componentId),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = getComponentDescription(componentId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isToggleable) {
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun getComponentDisplayName(componentId: String): String {
    return when (componentId) {
        "android.theme.customization.wifi_icon" -> "Wi‑Fi icons"
        "android.theme.customization.signal_icon" -> "Signal icons"
        "android.theme.customization.back_gesture" -> "Back gesture"
        "android.theme.customization.battery_style" -> "Battery style"
        "android.customization.sb_data" -> "Mobile data icons"
        "statusbar_wifi", "wifi" -> "WiFi Icons"
        "statusbar_signal", "signal" -> "Signal Icons"
        "android" -> "Android Framework"
        "systemui", "systemui_icons" -> "System UI"
        "ui_qs" -> "QuickSettings Style"
        "ui_volume" -> "Volume Panel Style"
        "ui_style" -> "UI Style"
        else -> componentId.replaceFirstChar { it.uppercase() }
    }
}

private fun getComponentDescription(componentId: String): String {
    return when (componentId) {
        "android.theme.customization.wifi_icon" -> "Wi‑Fi strength indicators in the status bar"
        "android.theme.customization.signal_icon" -> "Cellular signal indicators in the status bar"
        "android.theme.customization.back_gesture" -> "Back navigation gesture appearance"
        "android.theme.customization.battery_style" -> "Battery icon shape in the status bar"
        "android.customization.sb_data" -> "Network type labels (LTE, 5G, etc.) next to the signal icon"
        "statusbar_wifi", "wifi" -> "WiFi signal indicators in status bar"
        "statusbar_signal", "signal" -> "Mobile network indicators in status bar"
        "android" -> "Core Android framework icons"
        "systemui", "systemui_icons" -> "Status bar and quick settings icons"
        "ui_qs" -> "QuickSettings tiles and brightness slider style"
        "ui_volume" -> "Volume panel appearance"
        "ui_style" -> "Overall UI appearance"
        else -> "Theme component"
    }
}

@Composable
private fun ComponentSelectionItem(
    componentId: String,
    isEnabled: Boolean,
    isToggleable: Boolean,
    currentSource: String? = null,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = getComponentDisplayName(componentId),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (currentSource != null) {
                Text(
                    text = "Currently from: $currentSource",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else {
                Text(
                    text = getComponentDescription(componentId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (isToggleable) {
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        } else {
            Icon(
                imageVector = if (isEnabled) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ApplySection(
    theme: Theme,
    selectionState: ThemeSelectionState,
    hasPendingChanges: Boolean,
    onApplyClick: () -> Unit,
    onApplyPendingClick: () -> Unit,
    onDisableClick: () -> Unit,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (selectionState) {
            is ThemeSelectionState.Missing -> {
                Text(
                    text = stringResource(R.string.overlay_package_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is ThemeSelectionState.Error -> {
                Text(
                    text = stringResource(R.string.error_prefix, selectionState.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = {
                        onClearError()
                        onApplyClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
            is ThemeSelectionState.Inactive -> {
                val canApply =
                    if (theme.isUnified) hasPendingChanges else true
                Button(
                    onClick = onApplyClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canApply
                ) {
                    Text(stringResource(R.string.apply))
                }
            }
            is ThemeSelectionState.Active -> {
                if (theme.isUnified && hasPendingChanges) {
                    Button(
                        onClick = onApplyPendingClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply_changes))
                    }
                } else {
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.active))
                    }
                }
                OutlinedButton(
                    onClick = onDisableClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(stringResource(R.string.disable))
                }
            }
        }
    }
}

@Composable
private fun DetailPreviewBox(theme: Theme) {
    val packageName = theme.overlays.firstOrNull()?.packageName ?: ""
    val category = theme.category.ifEmpty { theme.overlays.firstOrNull()?.componentId ?: "" }
    val context = LocalContext.current
    val previewMap = remember {
        val map = mutableMapOf<String, String>()
        try {
            val entries = context.resources.getStringArray(R.array.overlay_preview_map)
            for (entry in entries) {
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2) map[parts[0]] = parts[1]
            }
        } catch (_: Exception) {}
        map
    }

    val prefix = previewMap[packageName] ?: ""
    val resIds = if (prefix.isNotEmpty()) {
        (1..4).mapNotNull { i ->
            val id = context.resources.getIdentifier("${prefix}_$i", "drawable", context.packageName)
            if (id != 0) id else null
        }
    } else emptyList()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            packageName.contains("battery", ignoreCase = true) ||
                category.contains("battery", ignoreCase = true) -> {
                BatteryStylePreview(
                    packageName = packageName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            packageName.contains("back_gesture", ignoreCase = true) ||
                category.contains("back_gesture", ignoreCase = true) -> {
                BackGesturePreview(modifier = Modifier.fillMaxSize())
            }
            resIds.isNotEmpty() -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    resIds.forEach { resId ->
                        val drawable = remember(resId) {
                            ContextCompat.getDrawable(context, resId)
                        }
                        if (drawable != null) {
                            Image(
                                bitmap = remember(drawable) {
                                    drawable.toBitmap().asImageBitmap()
                                },
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                colorFilter = ColorFilter.tint(
                                    MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
