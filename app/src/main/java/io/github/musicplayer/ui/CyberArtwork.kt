package io.github.musicplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import io.github.musicplayer.model.Track
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Original vector artwork; no game assets or service artwork are bundled. */
internal fun cyberAccent(track: Track): Color = when (Math.floorMod(track.artworkSeed, 6)) {
    0 -> Cyan
    1 -> Magenta
    2 -> Lime
    3 -> Color(0xFFFFAB68)
    4 -> Color(0xFFB8A1FF)
    else -> Color(0xFF6AF3B5)
}

@Composable
internal fun CyberCity(modifier: Modifier = Modifier, phase: Float = 0f) {
    Canvas(modifier) {
        if (size.minDimension <= 0f) return@Canvas
        drawRect(Brush.linearGradient(listOf(Color(0xFF102D37), Color(0xFF10152C), Color(0xFF080E19))))
        neonCity(Cyan, phase, 7)
    }
}

@Composable
internal fun CyberCover(track: Track, modifier: Modifier = Modifier, phase: Float = 0f) {
    val accent = cyberAccent(track)
    val seed = Math.floorMod(track.artworkSeed, 6)
    Canvas(modifier) {
        if (size.minDimension <= 0f) return@Canvas
        val w = size.width
        val h = size.height
        val center = Offset(w * .5f, h * .47f)
        drawRect(Brush.linearGradient(listOf(accent.copy(.24f), Color(0xFF0D1421), Color(0xFF080D16))))
        repeat(9) { i ->
            val x = w * (i / 8f)
            drawLine(accent.copy(.075f), Offset(x, 0f), Offset(x, h), w * .002f)
            drawLine(accent.copy(.075f), Offset(0f, h * i / 8), Offset(w, h * i / 8), w * .002f)
        }
        when (seed) {
            0, 3 -> neonCity(accent, phase / 360f, seed + 3)
            1, 4 -> {
                drawCircle(Brush.radialGradient(listOf(accent.copy(.27f), Color.Transparent), center, w * .55f), w * .55f, center)
                rotate(-25f + phase * .08f, center) {
                    repeat(11) { i ->
                        val r = w * (.12f + i * .021f)
                        drawOval(accent.copy(.23f + i * .06f), Offset(center.x - r, center.y - r * .74f), Size(r * 2, r * 1.48f), style = Stroke(w * .004f))
                    }
                    drawLine(Milk.copy(.85f), Offset(w * .13f, h * .68f), Offset(w * .87f, h * .27f), w * .005f)
                    drawCircle(Lime, w * .018f, Offset(w * .8f, h * .31f))
                }
                repeat(6) { i ->
                    val y = h * .77f + i * h * .016f
                    drawLine(accent.copy(.6f - i * .08f), Offset(w * .14f, y), Offset(w * (.86f - i * .045f), y), w * .002f)
                }
            }
            else -> {
                drawCircle(Brush.radialGradient(listOf(accent.copy(.2f), Color.Transparent), center, w * .55f), w * .55f, center)
                rotate(phase * .05f, center) {
                    repeat(12) { i ->
                        val radius = w * (.1f + i * .024f)
                        val path = Path()
                        repeat(4) { point ->
                            val angle = (-90 + point * 120) * PI / 180
                            val x = center.x + radius * cos(angle).toFloat()
                            val y = center.y + radius * sin(angle).toFloat()
                            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        drawPath(path, accent.copy(.15f + i * .055f), style = Stroke(w * .005f))
                    }
                }
                drawLine(Cyan.copy(.9f), Offset(w * .13f, h * .72f), Offset(w * .87f, h * .72f), w * .005f)
            }
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(.65f))))
        // Small registration marks and a barcode give the covers a physical sleeve identity.
        drawLine(accent, Offset(w * .075f, h * .085f), Offset(w * .22f, h * .085f), w * .005f)
        drawLine(accent, Offset(w * .075f, h * .085f), Offset(w * .075f, h * .16f), w * .005f)
        repeat(18) { i ->
            val x = w * (.62f + i * .014f)
            drawLine(Milk.copy(if (i % 3 == 0) .8f else .35f), Offset(x, h * .88f), Offset(x, h * .94f), w * if (i % 4 == 0) .007f else .003f)
        }
        drawLine(accent.copy(.65f), Offset(w * .075f, h * .93f), Offset(w * .35f, h * .93f), w * .004f)
    }
}

private fun DrawScope.neonCity(accent: Color, phase: Float, seed: Int) {
    val w = size.width
    val h = size.height
    val sun = Offset(w * .65f, h * .3f)
    drawCircle(Brush.radialGradient(listOf(accent.copy(.3f), Color.Transparent), sun, w * .48f), w * .48f, sun)
    drawCircle(accent.copy(.24f), w * .19f, sun, style = Stroke(w * .004f))
    drawCircle(accent.copy(.13f), w * .205f, sun, style = Stroke(w * .002f))
    repeat(3) { layer ->
        val count = 12 + layer * 2
        val base = h * (.64f + layer * .11f)
        repeat(count) { i ->
            val width = w / (count - 1f)
            val x = i * width - width * .3f
            val height = h * (.09f + Math.floorMod(i * 7 + seed * 3 + layer * 11, 13) / 45f)
            val top = base - height
            val building = if (layer == 0) Color(0xFF1B3343) else if (layer == 1) Color(0xFF101E2C) else Color(0xFF0A121D)
            drawRect(building, Offset(x, top), Size(width * .92f, height))
            drawLine(accent.copy(.18f + layer * .12f), Offset(x + width * .9f, top), Offset(x + width * .9f, base), w * .002f)
            if (i % 3 == 0) {
                drawLine(accent.copy(.65f), Offset(x + width * .48f, top), Offset(x + width * .48f, top - height * .19f), w * .002f)
                drawCircle(if (i % 2 == 0) Magenta else accent, w * .004f, Offset(x + width * .48f, top - height * .19f))
            }
            repeat(4) { col ->
                repeat(7) { row ->
                    if ((i + row * 3 + col * 5 + seed) % 4 != 0) {
                        drawRect((if ((i + col) % 5 == 0) Magenta else accent).copy(.16f + layer * .13f), Offset(x + width * (.14f + col * .17f), top + height * (.13f + row * .11f)), Size(width * .07f, h * .006f))
                    }
                }
            }
        }
    }
    val horizon = h * .82f
    repeat(10) { i ->
        val targetX = w * (i - 4) * .42f
        drawLine(accent.copy(.15f), Offset(w * .6f, horizon), Offset(targetX, h), w * .002f)
    }
    repeat(6) { i ->
        val p = i / 5f
        val y = horizon + (h - horizon) * p * p
        drawLine(accent.copy(.17f), Offset(0f, y), Offset(w, y), w * .002f)
    }
    val lightY = h * (.29f + .1f * sin(phase * 2f * PI).toFloat())
    drawLine(Magenta.copy(.7f), Offset(w * .4f, lightY), Offset(w * .77f, lightY - h * .06f), w * .003f)
}
