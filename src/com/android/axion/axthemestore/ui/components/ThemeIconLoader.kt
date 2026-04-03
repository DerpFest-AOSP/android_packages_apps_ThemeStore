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

package com.android.axion.axthemestore.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThemeIconLoader {
    private const val TAG = "ThemeIconLoader"
    
    suspend fun loadThemeIcons(context: Context, packageName: String): List<Drawable> = 
        withContext(Dispatchers.IO) {
            val icons = mutableListOf<Drawable>()
            
            try {
                val pm = context.packageManager
                val resources = pm.getResourcesForApplication(packageName)
                
                val commonIconNames = listOf(
                    "stat_sys_wifi_signal_4",
                    "ic_wifi_signal_4",
                    "stat_sys_signal_cellular_4_4_bar",
                    "ic_signal_cellular_4_4_bar",
                    "ic_lte_mobiledata",
                    "ic_5g_mobiledata",
                    "ic_4g_mobiledata",
                    "ic_qs_bluetooth",
                    "ic_qs_flashlight",
                    "ic_qs_airplane",
                    "ic_settings",
                    "ic_launcher"
                )
                
                for (iconName in commonIconNames) {
                    if (icons.size >= 6) break
                    
                    try {
                        val resId = resources.getIdentifier(iconName, "drawable", packageName)
                        if (resId != 0) {
                            val typedValue = android.util.TypedValue()
                            resources.getValue(resId, typedValue, true)
                            
                            val drawable = if (typedValue.string?.toString()?.endsWith(".xml") == true) {
                                Drawable.createFromXml(resources, resources.getXml(resId))
                            } else {
                                resources.openRawResource(resId).use { inputStream ->
                                    val bitmap = BitmapFactory.decodeStream(inputStream)
                                    if (bitmap != null) {
                                        BitmapDrawable(resources, bitmap)
                                    } else null
                                }
                            }
                            
                            if (drawable != null) {
                                icons.add(drawable)
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
                
                if (icons.size < 6) {
                    val arrayNames = listOf(
                        "target_android",
                        "target_systemui", 
                        "target_wifi",
                        "target_signal"
                    )
                    
                    for (arrayName in arrayNames) {
                        if (icons.size >= 6) break
                        
                        try {
                            val arrayId = resources.getIdentifier(arrayName, "array", packageName)
                            if (arrayId != 0) {
                                val drawableNames = resources.getStringArray(arrayId)
                                val shuffledNames = drawableNames.toList().shuffled()
                                
                                for (drawableName in shuffledNames) {
                                    if (icons.size >= 6) break
                                    
                                    try {
                                        val resId = resources.getIdentifier(drawableName, "drawable", packageName)
                                        if (resId != 0) {
                                            val typedValue = android.util.TypedValue()
                                            resources.getValue(resId, typedValue, true)
                                            
                                            val drawable = if (typedValue.string?.toString()?.endsWith(".xml") == true) {
                                                Drawable.createFromXml(resources, resources.getXml(resId))
                                            } else {
                                                resources.openRawResource(resId).use { inputStream ->
                                                    val bitmap = BitmapFactory.decodeStream(inputStream)
                                                    if (bitmap != null) {
                                                        BitmapDrawable(resources, bitmap)
                                                    } else null
                                                }
                                            }
                                            
                                            if (drawable != null && !icons.contains(drawable)) {
                                                icons.add(drawable)
                                            }
                                        }
                                    } catch (e: Exception) {
                                    }
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
                
                if (icons.isEmpty()) {
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val appIcon = pm.getApplicationIcon(appInfo)
                        icons.add(appIcon)
                    } catch (e: Exception) {
                        Log.d(TAG, "Could not load app icon for $packageName")
                    }
                }
                
                Log.d(TAG, "Loaded ${icons.size} icons for $packageName")
                
            } catch (e: PackageManager.NameNotFoundException) {
                Log.d(TAG, "Package not installed: $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load icons from $packageName", e)
            }
            
            icons
        }
}

@Composable
fun ThemePackagePreview(
    packageName: String,
    modifier: Modifier = Modifier,
    showSingleIcon: Boolean = false
) {
    val context = LocalContext.current
    var icons by remember(packageName) { mutableStateOf<List<Drawable>>(emptyList()) }
    
    LaunchedEffect(packageName) {
        icons = ThemeIconLoader.loadThemeIcons(context, packageName)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icons.isNotEmpty()) {
            if (showSingleIcon) {
                DrawableIcon(
                    drawable = icons.first(),
                    size = 36.dp,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    icons.take(6).forEach { drawable ->
                        DrawableIcon(
                            drawable = drawable,
                            size = 48.dp,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawableIcon(
    drawable: Drawable,
    size: Dp,
    tint: Color
) {
    val density = LocalDensity.current
    val bitmap = remember(drawable, size) {
        drawable.toBitmap(
            width = with(density) { size.toPx().toInt() },
            height = with(density) { size.toPx().toInt() }
        )
    }
    
    val tintedBitmap = remember(bitmap, tint) {
        val paint = Paint().apply {
            colorFilter = PorterDuffColorFilter(
                tint.toArgb(),
                PorterDuff.Mode.SRC_IN
            )
        }
        
        val result = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        result
    }
    
    Image(
        bitmap = tintedBitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.size(size)
    )
}
