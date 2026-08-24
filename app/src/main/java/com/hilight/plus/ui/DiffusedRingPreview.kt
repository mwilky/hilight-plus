package com.hilight.plus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reusable diffused camera ring preview mimicking the Pixel 11 rear micro-LED array.
 * Adapts dynamically to light and dark theme surfaces with no heavy solid container background.
 */
@Composable
fun DiffusedRingPreview(
    frames: IntArray,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp
) {
    val inactiveChannelColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val frostedLensColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val ringBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val ringRadius = this.size.minDimension * 0.35f
            val channelStrokeWidth = 11.dp.toPx()
            val spotRadius = this.size.minDimension * 0.18f

            // Base unlit translucent channel ring (themed to current light/dark palette)
            drawCircle(
                color = inactiveChannelColor,
                radius = ringRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = channelStrokeWidth)
            )

            // Outer & Inner subtle lens edge outlines for clean definition on any theme background
            drawCircle(
                color = ringBorderColor,
                radius = ringRadius + (channelStrokeWidth / 2f),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = ringBorderColor,
                radius = (ringRadius - (channelStrokeWidth / 2f)).coerceAtLeast(1f),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )

            // Render diffused radial glow spots for each LED
            for (i in 0 until 8) {
                val angle = (i * (2 * PI / 8.0) - (PI / 2.0))
                val spotCenter = Offset(
                    x = center.x + (ringRadius * cos(angle)).toFloat(),
                    y = center.y + (ringRadius * sin(angle)).toFloat()
                )

                val c = frames.getOrElse(i) { 0x00000000 }
                val alpha = (c ushr 24) and 0xFF
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val maxChannel = maxOf(r, g, b)

                // Only render glow if the color has non-black RGB luminance
                if (alpha > 0 && maxChannel > 5) {
                    val ledColor = Color(c)
                    val intensity = (maxChannel / 255f)

                    // Outer soft diffusion flare
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ledColor.copy(alpha = 0.95f),
                                ledColor.copy(alpha = 0.55f),
                                ledColor.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            center = spotCenter,
                            radius = spotRadius * 1.4f
                        ),
                        radius = spotRadius * 1.4f,
                        center = spotCenter
                    )

                    // High-intensity core emitter
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.70f * intensity),
                                ledColor.copy(alpha = 0.95f)
                            ),
                            center = spotCenter,
                            radius = spotRadius * 0.45f
                        ),
                        radius = spotRadius * 0.45f,
                        center = spotCenter
                    )
                }
            }

            // Translucent frosted glass cap
            drawCircle(
                color = frostedLensColor,
                radius = ringRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = channelStrokeWidth)
            )
        }
    }
}
