package com.hilight.plus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 */
@Composable
fun DiffusedRingPreview(
    frames: IntArray,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFF16181C)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val inactiveColor = Color(0xFF282B30)
            val frostedLensColor = Color(0x33FFFFFF)

            Canvas(modifier = Modifier.size(size)) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val ringRadius = this.size.minDimension * 0.35f
                val spotRadius = this.size.minDimension * 0.18f

                // Base unlit translucent channel ring
                drawCircle(
                    color = inactiveColor,
                    radius = ringRadius + 5.dp.toPx(),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12.dp.toPx())
                )

                // Render diffused radial glow spots for each LED
                for (i in 0 until 8) {
                    val angle = (i * (2 * PI / 8.0) - (PI / 2.0))
                    val spotCenter = Offset(
                        x = center.x + (ringRadius * cos(angle)).toFloat(),
                        y = center.y + (ringRadius * sin(angle)).toFloat()
                    )

                    val c = frames.getOrElse(i) { 0x00000000 }
                    val isLit = (c ushr 24) > 0 && ((c and 0x00FFFFFF) != 0)

                    if (isLit) {
                        val ledColor = Color(c)
                        // Soft outer diffusion glow blending adjacent LEDs
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ledColor.copy(alpha = 0.95f),
                                    ledColor.copy(alpha = 0.65f),
                                    ledColor.copy(alpha = 0.25f),
                                    Color.Transparent
                                ),
                                center = spotCenter,
                                radius = spotRadius * 1.5f
                            ),
                            radius = spotRadius * 1.5f,
                            center = spotCenter
                        )

                        // Bright core emitter
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.85f),
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

                // Frosted glass diffusion overlay ring on top
                drawCircle(
                    color = frostedLensColor,
                    radius = ringRadius + 5.dp.toPx(),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12.dp.toPx())
                )
            }
        }
    }
}
