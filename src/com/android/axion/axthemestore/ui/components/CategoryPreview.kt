/*
 * Copyright (C) 2025-2026 AxionOS Project
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

package com.android.axion.axthemestore.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.PathParser
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.min

private data class BatteryShapeSpec(
    val pathData: String,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val fillPathData: String? = null,
    val accentPathData: String? = null,
    /**
     * When true, scale X and Y independently to fill the preview (same idea as the status bar drawable).
     * Use for tall design boxes that should read as landscape in the status bar.
     */
    val nonUniformScale: Boolean = false,
    /** When [nonUniformScale] is true, multiplies vertical scale only (values below 1 shorten the preview). */
    val nonUniformScaleYMultiplier: Float = 1f,
    /** Multiplied by min(scaleX, scaleY) for outline stroke width. */
    val strokeWidthFactor: Float = 0.7f,
    /** Extra scale for theme-card preview only (status bar uses config_batteryMeterSlotScalePercent on device). */
    val previewScaleMultiplier: Float = 1f,
)

private val BATTERY_SHAPES = mapOf(
    "oneui7" to BatteryShapeSpec(
        pathData = "M6,0 C2.69,0 0,2.69 0,6 C0,9.31 2.69,12 6,12 L18,12 C21.31,12 24,9.31 24,6 C24,2.69 21.31,0 18,0 L6,0 Z",
        viewportWidth = 24f,
        viewportHeight = 12f,
    ),
    "ios15" to BatteryShapeSpec(
        pathData = "M3.79,0.78L18.21,0.78A2.82 2.82 0 0 1 21.03,3.60L21.03,8.40A2.82 2.82 0 0 1 18.21,11.22L3.79,11.22A2.82 2.82 0 0 1 0.97,8.40L0.97,3.60A2.82 2.82 0 0 1 3.79,0.78zM21.82,7.59C23.43,7.11,23.43,4.89,21.82,4.41L21.82,7.59z",
        viewportWidth = 24f,
        viewportHeight = 12f,
        fillPathData = "M2.00,3.75L2.00,8.25A2.00 2.00 0 0 0 4.00,10.25L18.00,10.25A2.00 2.00 0 0 0 20.00,8.25L20.00,3.75A2.00 2.00 0 0 0 18.00,1.75L4.00,1.75A2.00 2.00 0 0 0 2.00,3.75z",
    ),
    "ios16" to BatteryShapeSpec(
        pathData = "M3.13,0.36L18.71,0.36A3.04 3.04 0 0 1 21.76,3.40L21.76,8.60A3.04 3.04 0 0 1 18.71,11.64L3.13,11.64A3.04 3.04 0 0 1 0.08,8.60L0.08,3.40A3.04 3.04 0 0 1 3.13,0.36zM22.61,7.72C24.35,7.19,24.35,4.81,22.61,4.28L22.61,7.72z",
        viewportWidth = 24f,
        viewportHeight = 12f,
    ),
    "origami" to BatteryShapeSpec(
        pathData = "M15.50,0.50C13.50,0.33,11.00,0.33,8.50,0.50C8.00,-0.21,6.00,-0.21,5.63,1.00C5.17,2.50,4.33,2.33,4.00,4.33C3.75,5.50,3.75,7.50,4.00,8.43C4.25,9.50,5.50,12.00,8.95,12.00L15.00,12.00C18.45,12.00,19.70,9.50,19.95,8.43C20.20,7.50,20.20,5.50,19.95,4.33C19.62,2.33,18.79,2.50,18.33,1.00C18.00,-0.21,16.00,-0.21,15.50,0.50z",
        viewportWidth = 20f,
        viewportHeight = 12f,
        fillPathData = "M15.25,10.50C12.50,10.57,11.50,10.57,8.75,10.50C7.50,10.40,6.00,9.75,5.50,7.75Q5.12,6.00,5.50,4.50C6.00,3.00,7.50,2.10,8.75,2.00C11.00,1.88,13.00,1.88,15.25,2.00C16.50,2.10,18.00,3.00,18.50,4.50Q18.88,6.00,18.50,7.75C18.00,9.75,16.50,10.40,15.25,10.50z",
    ),
    /** Upright heart: padded uniform scale, biased up, narrowed horizontally (matches BatteryStyleHeart overlay). */
    /** No accent in preview — bolt path reads as a second heart when drawn filled; bolt still in overlay. */
    "heart" to BatteryShapeSpec(
        pathData = "M12.00,11.38Q11.65,11.38,11.43,11.15L8.10,7.80C7.30,6.96,6.72,6.41,6.72,4.78C6.99,1.36,10.13,0.34,12.00,2.89C13.87,0.34,17.01,1.36,17.28,4.78C17.28,6.41,16.70,6.96,15.90,7.80L12.57,11.15Q12.35,11.38,12.00,11.38z",
        viewportWidth = 24f,
        viewportHeight = 12f,
        fillPathData = "M12.00,11.38Q11.65,11.38,11.43,11.15L8.10,7.80C7.30,6.96,6.72,6.41,6.72,4.78C6.99,1.36,10.13,0.34,12.00,2.89C13.87,0.34,17.01,1.36,17.28,4.78C17.28,6.41,16.70,6.96,15.90,7.80L12.57,11.15Q12.35,11.38,12.00,11.38z",
        strokeWidthFactor = 0.55f,
        previewScaleMultiplier = 1.95f,
    ),
    "smiley" to BatteryShapeSpec(
        pathData = "M3.76,0.62L18.03,0.62A2.74 2.74 0 0 1 20.78,3.36L20.78,8.64A2.74 2.74 0 0 1 18.03,11.38L3.76,11.38A2.74 2.74 0 0 1 1.02,8.64L1.02,3.36A2.74 2.74 0 0 1 3.76,0.62zM21.66,7.79C23.42,7.68,23.42,4.32,21.66,4.21L21.66,7.79z",
        viewportWidth = 22f,
        viewportHeight = 12f,
        fillPathData = "M1.93,3.37L1.93,8.66A1.85 1.85 0 0 0 3.78,10.51L18.04,10.51A1.85 1.85 0 0 0 19.89,8.66L19.89,3.37A1.85 1.85 0 0 0 18.04,1.52L3.78,1.52A1.85 1.85 0 0 0 1.93,3.37z",
        accentPathData = "M12.21,6.01C12.05,7.95,9.45,7.95,9.29,6.01C9.34,5.36,10.21,5.36,10.27,6.01C10.22,6.66,11.28,6.66,11.23,6.01C11.29,5.36,12.16,5.36,12.21,6.01zM15.62,5.03C16.16,5.03,16.59,5.47,16.59,6.01C16.59,6.54,16.16,6.98,15.62,6.98C15.08,6.98,14.65,6.54,14.65,6.01C14.65,5.47,15.08,5.03,15.62,5.03zM5.88,5.03C6.42,5.03,6.86,5.47,6.86,6.01C6.86,6.54,6.42,6.98,5.88,6.98C5.34,6.98,4.91,6.54,4.91,6.01C4.91,5.47,5.34,5.03,5.88,5.03z",
    ),
    /** Full perimeter from overlay (24-wide); first bar matches pill geometry like bars 2–3. */
    "faintui" to BatteryShapeSpec(
        pathData = "M3.6,0L17.9,0A3.6,3,0,0,1,21.5,3L21.5,12A3.6,3,0,0,1,17.9,15L3.6,15A3.6,3,0,0,1,0,12L0,3A3.6,3,0,0,1,3.6,0ZM12.08,3L12.08,12A0.6,0.5,0,0,0,12.68,12.5L13.48,12.5A0.6,0.5,0,0,0,14.08,12L14.08,3A0.6,0.5,0,0,0,13.48,2.5L12.68,2.5A0.6,0.5,0,0,0,12.08,3ZM8.58,3L8.58,12A0.6,0.5,0,0,0,9.18,12.5L9.98,12.5A0.6,0.5,0,0,0,10.58,12L10.58,3A0.6,0.5,0,0,0,9.98,2.5L9.18,2.5A0.6,0.5,0,0,0,8.58,3ZM4.66,3L4.66,12A0.6,0.5,0,0,0,5.26,12.5L6.06,12.5A0.6,0.5,0,0,0,6.66,12L6.66,3A0.6,0.5,0,0,0,6.06,2.5L5.26,2.5A0.6,0.5,0,0,0,4.66,3ZM21.5,10.83333L21.5,9.46667L22,9.46667Q22.5,9.46667,22.5,9.05L22.5,5.95Q22.5,5.53333,22,5.53333L21.5,5.53333L21.5,4.16667L22.5,4.16667Q24,4.16667,24,5.41667L24,9.58333Q24,10.83333,22.5,10.83333L21.5,10.83333ZM1.84,3.43333L1.84,11.56667A2.28,1.9,0,0,0,4.12,13.46667L17.22,13.46667A2.28,1.9,0,0,0,19.5,11.56667L19.5,3.43333A2.28,1.9,0,0,0,17.22,1.53333L4.12,1.53333A2.28,1.9,0,0,0,1.84,3.43333Z",
        viewportWidth = 24f,
        viewportHeight = 15f,
        fillPathData = "M1.84,3.43333L1.84,11.56667A2.28,1.9,0,0,0,4.12,13.46667L17.22,13.46667A2.28,1.9,0,0,0,19.5,11.56667L19.5,3.43333A2.28,1.9,0,0,0,17.22,1.53333L4.12,1.53333A2.28,1.9,0,0,0,1.84,3.43333Z",
        nonUniformScale = true,
        nonUniformScaleYMultiplier = 0.72f,
        strokeWidthFactor = 0.28f,
    ),
    /** Rounded body + plus / cutout detail (BatteryStyleBatteryInverter). Preview scale / stroke match heart style. */
    "batteryinverter" to BatteryShapeSpec(
        pathData =
            "M1.82,5.31L10.18,5.31A1.82 1.82 0 0 1 12.00,7.13L12.00,13.18A1.82 1.82 0 0 1 10.18,15.00L1.82,15.00A1.82 1.82 0 0 1 0.00,13.18L0.00,7.13A1.82 1.82 0 0 1 1.82,5.31z" +
                "M3.62,10.76L4.27,10.76C5.00,10.76,5.00,9.55,4.27,9.55L3.62,9.55L3.62,8.88C3.62,8.12,2.44,8.12,2.44,8.88L2.44,9.55L1.79,9.55C1.06,9.55,1.06,10.76,1.79,10.76L2.44,10.76L2.44,11.43C2.44,12.18,3.62,12.18,3.62,11.43L3.62,10.76" +
                "M7.18,10.15L7.18,10.15A0.61 0.61 0 0 0 7.78,10.76L10.10,10.76A0.61 0.61 0 0 0 10.71,10.15L10.71,10.15A0.61 0.61 0 0 0 10.10,9.55L7.78,9.55A0.61 0.61 0 0 0 7.18,10.15z" +
                "M9.53,5.31L9.53,5.01C9.53,3.80,7.76,3.80,7.76,5.01L7.76,5.31M4.24,5.31L4.24,5.01C4.24,3.80,2.47,3.80,2.47,5.01L2.47,5.31",
        viewportWidth = 12f,
        viewportHeight = 15f,
        fillPathData =
            "M1.82,5.31L10.18,5.31A1.82 1.82 0 0 1 12.00,7.13L12.00,13.18A1.82 1.82 0 0 1 10.18,15.00L1.82,15.00A1.82 1.82 0 0 1 0.00,13.18L0.00,7.13A1.82 1.82 0 0 1 1.82,5.31z" +
                "M3.62,10.76L4.27,10.76C5.00,10.76,5.00,9.55,4.27,9.55L3.62,9.55L3.62,8.88C3.62,8.12,2.44,8.12,2.44,8.88L2.44,9.55L1.79,9.55C1.06,9.55,1.06,10.76,1.79,10.76L2.44,10.76L2.44,11.43C2.44,12.18,3.62,12.18,3.62,11.43L3.62,10.76" +
                "M7.18,10.15L7.18,10.15A0.61 0.61 0 0 0 7.78,10.76L10.10,10.76A0.61 0.61 0 0 0 10.71,10.15L10.71,10.15A0.61 0.61 0 0 0 10.10,9.55L7.78,9.55A0.61 0.61 0 0 0 7.18,10.15z" +
                "M9.53,5.31L9.53,5.01C9.53,3.80,7.76,3.80,7.76,5.01L7.76,5.31M4.24,5.31L4.24,5.01C4.24,3.80,2.47,3.80,2.47,5.01L2.47,5.31",
        strokeWidthFactor = 0.55f,
    ),
)

@Composable
fun BatteryStylePreview(packageName: String, modifier: Modifier = Modifier) {
    val shapeKey = packageName.substringAfterLast('.')
    val spec = BATTERY_SHAPES[shapeKey] ?: return

    val strokeColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val fillColorArgb = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f).toArgb()
    val accentColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()

    val basePath: Path = remember(spec) { PathParser.createPathFromPathData(spec.pathData) }
    val baseFillPath: Path? = remember(spec) {
        spec.fillPathData?.let { PathParser.createPathFromPathData(it) }
    }
    val baseAccentPath: Path? = remember(spec) {
        spec.accentPathData?.let { PathParser.createPathFromPathData(it) }
    }

    Box(
        modifier = modifier.drawWithContent {
            val vw = spec.viewportWidth
            val vh = spec.viewportHeight
            val pad = 0.56f
            val sx: Float
            val sy: Float
            if (spec.nonUniformScale) {
                sx = size.width * pad / vw * spec.previewScaleMultiplier
                sy = size.height * pad / vh * spec.nonUniformScaleYMultiplier * spec.previewScaleMultiplier
            } else {
                val s = min(size.width / vw, size.height / vh) * 0.64f * spec.previewScaleMultiplier
                sx = s
                sy = s
            }
            val dx = (size.width - vw * sx) / 2f
            val dy = (size.height - vh * sy) / 2f
            val matrix = Matrix().apply { setScale(sx, sy); postTranslate(dx, dy) }
            val strokePx = min(sx, sy) * spec.strokeWidthFactor

            val outlinePath = Path(basePath).apply { transform(matrix) }
            val fillPath: Path? = baseFillPath?.let { Path(it).apply { transform(matrix) } }
            val accentPath: Path? = baseAccentPath?.let { Path(it).apply { transform(matrix) } }

            drawIntoCanvas { canvas ->
                fillPath?.let {
                    canvas.nativeCanvas.drawPath(it, Paint().apply {
                        color = fillColorArgb
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    })
                }
                canvas.nativeCanvas.drawPath(outlinePath, Paint().apply {
                    color = strokeColorArgb
                    strokeWidth = strokePx
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                })
                accentPath?.let {
                    canvas.nativeCanvas.drawPath(it, Paint().apply {
                        color = accentColorArgb
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    })
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
fun BackGesturePreview(modifier: Modifier = Modifier) {
    val dotColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier.drawWithContent {
            drawDotTrail(dotColor)
        },
        contentAlignment = Alignment.Center,
    ) {}
}

private fun DrawScope.drawDotTrail(color: Color) {
    val d = density
    val lengthPx = 12f * d
    val heightPx = 13.5f * d
    val strokePx = 1.5f * d
    val scaleFactor = min(size.width, size.height) * 0.4f / (heightPx * 2f)

    val r = strokePx * scaleFactor
    val scaledX = lengthPx * scaleFactor
    val scaledY = heightPx * scaleFactor
    val originX = size.width / 2f - scaledX / 2f
    val cy = size.height / 2f

    drawCircle(color, radius = r, center = Offset(originX, cy))
    for (i in 1..3) {
        val ratio = i.toFloat() / 3f
        val xPos = originX + scaledX * ratio
        val yPos = scaledY * ratio
        drawCircle(color, radius = r, center = Offset(xPos, cy + yPos))
        drawCircle(color, radius = r, center = Offset(xPos, cy - yPos))
    }
}
