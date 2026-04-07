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

package com.android.axion.axthemestore.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.axion.axthemestore.R
import com.android.axion.axthemestore.data.model.Theme
import com.android.axion.axthemestore.data.model.ThemeSelectionState
import com.android.axion.axthemestore.data.model.formatFileSize

@Composable
fun ThemeCard(
    theme: Theme,
    selectionState: ThemeSelectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.extraLarge)
            ) {
                LocalPreviewBox(theme = theme)
                
                SelectionStateBadge(
                    state = selectionState,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }
            
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (theme.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = theme.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = theme.author,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    
                    if (theme.totalFileSize > 0) {
                        Text(
                            text = theme.totalFileSize.formatFileSize(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionStateBadge(
    state: ThemeSelectionState,
    modifier: Modifier = Modifier
) {
    val (icon, backgroundColor, contentColor) = when (state) {
        is ThemeSelectionState.Active -> Triple(
            Icons.Default.Check,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        is ThemeSelectionState.Inactive -> Triple(
            Icons.Default.TouchApp,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        is ThemeSelectionState.Missing -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        is ThemeSelectionState.Error -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }
    
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun LocalPreviewBox(theme: Theme) {
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

    val packageName = theme.overlays.firstOrNull()?.packageName ?: ""
    val category = theme.category.ifEmpty { theme.overlays.firstOrNull()?.componentId ?: "" }
    val isBattery =
        packageName.contains("battery", ignoreCase = true) ||
            category.contains("battery", ignoreCase = true)
    val isBackGesture =
        packageName.contains("back_gesture", ignoreCase = true) ||
            category.contains("back_gesture", ignoreCase = true)
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (resIds.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                resIds.forEach { resId ->
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        colorFilter = ColorFilter.tint(
                            MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        } else if (isBattery) {
            BatteryStylePreview(
                packageName = packageName,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isBackGesture) {
            BackGesturePreview(modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                imageVector = categoryIcon(theme),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun categoryIcon(theme: Theme): ImageVector {
    val category = theme.category.ifEmpty {
        theme.overlays.firstOrNull()?.componentId ?: ""
    }
    return when {
        "charging" in category.lowercase() -> Icons.Default.BatteryChargingFull
        "battery" in category.lowercase() -> Icons.Default.BatteryFull
        "wifi" in category.lowercase() -> Icons.Default.Wifi
        "signal" in category.lowercase() -> Icons.Default.SignalCellularAlt
        "icon_pack" in category.lowercase() -> Icons.Default.Apps
        "back_gesture" in category.lowercase() -> Icons.Default.Gesture
        "volume" in category.lowercase() -> Icons.Default.VolumeUp
        "ui_qs" in category.lowercase() || "qs" in category.lowercase() -> Icons.Default.Dashboard
        else -> Icons.Default.Palette
    }
}
