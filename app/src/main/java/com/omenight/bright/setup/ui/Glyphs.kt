package com.omenight.bright.setup.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.LocalContentColor
import com.omenight.bright.setup.ui.theme.BrightColors

/** Icons drawn in code like Bright's, so the APK ships no icon set. */
enum class Glyph { Backspace, Check, Chevron, Ethernet, Lock, Shift }

@Composable
fun GlyphIcon(glyph: Glyph, size: Dp, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            Glyph.Backspace -> backspace(color, stroke)
            Glyph.Check -> drawPath(
                Path().apply {
                    moveTo(w * 0.22f, w * 0.52f)
                    lineTo(w * 0.42f, w * 0.72f)
                    lineTo(w * 0.80f, w * 0.30f)
                },
                color,
                style = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            Glyph.Chevron -> drawPath(
                Path().apply {
                    moveTo(w * 0.38f, w * 0.22f)
                    lineTo(w * 0.66f, w * 0.50f)
                    lineTo(w * 0.38f, w * 0.78f)
                },
                color,
                style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            Glyph.Ethernet -> ethernet(color)
            Glyph.Lock -> lock(color, stroke)
            Glyph.Shift -> drawPath(
                Path().apply {
                    moveTo(w * 0.50f, w * 0.16f)
                    lineTo(w * 0.84f, w * 0.52f)
                    lineTo(w * 0.64f, w * 0.52f)
                    lineTo(w * 0.64f, w * 0.82f)
                    lineTo(w * 0.36f, w * 0.82f)
                    lineTo(w * 0.36f, w * 0.52f)
                    lineTo(w * 0.16f, w * 0.52f)
                    close()
                },
                color,
                style = stroke,
            )
        }
    }
}

/** Bright's Wi-Fi status glyph, a dot plus three arcs lit up to [bars]. */
@Composable
fun WifiGlyph(bars: Int, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val lit = BrightColors.Text
        val dim = BrightColors.Text.copy(alpha = 0.25f)
        val stroke = this.size.width * 0.09f
        val dot = Offset(center.x, this.size.height * 0.80f)
        drawCircle(lit, radius = stroke * 0.8f, center = dot)
        for (ring in 1..3) {
            val radius = this.size.width * (0.17f + 0.15f * ring)
            drawArc(
                color = if (ring <= bars) lit else dim,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(dot.x - radius, dot.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.backspace(color: Color, stroke: Stroke) {
    val w = size.width
    val body = Path().apply {
        moveTo(w * 0.34f, w * 0.24f)
        lineTo(w * 0.88f, w * 0.24f)
        lineTo(w * 0.88f, w * 0.76f)
        lineTo(w * 0.34f, w * 0.76f)
        lineTo(w * 0.10f, w * 0.50f)
        close()
    }
    drawPath(body, color, style = stroke)
    drawLine(color, Offset(w * 0.48f, w * 0.38f), Offset(w * 0.72f, w * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.72f, w * 0.38f), Offset(w * 0.48f, w * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.lock(color: Color, stroke: Stroke) {
    val w = size.width
    drawRoundRect(color, topLeft = Offset(w * 0.22f, w * 0.44f), size = Size(w * 0.56f, w * 0.42f), cornerRadius = CornerRadius(w * 0.08f))
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.32f, w * 0.16f),
        size = Size(w * 0.36f, w * 0.40f),
        style = stroke,
    )
    drawLine(color, Offset(w * 0.32f, w * 0.36f), Offset(w * 0.32f, w * 0.46f), strokeWidth = stroke.width)
    drawLine(color, Offset(w * 0.68f, w * 0.36f), Offset(w * 0.68f, w * 0.46f), strokeWidth = stroke.width)
}

// A network jack seen head-on, as in Bright's status corner
private fun DrawScope.ethernet(color: Color) {
    val stroke = size.width * 0.09f
    val body = Size(size.width * 0.70f, size.height * 0.55f)
    val topLeft = Offset((size.width - body.width) / 2, size.height * 0.28f)
    drawRoundRect(color, topLeft = topLeft, size = body, cornerRadius = CornerRadius(stroke), style = Stroke(width = stroke))
    val clip = Size(body.width * 0.36f, body.height * 0.30f)
    drawRect(color, topLeft = Offset(center.x - clip.width / 2, topLeft.y + body.height - clip.height - stroke / 2), size = clip)
    for (pin in 0..3) {
        val x = topLeft.x + body.width * (0.26f + pin * 0.16f)
        drawLine(color, Offset(x, topLeft.y + stroke * 1.6f), Offset(x, topLeft.y + body.height * 0.42f), strokeWidth = stroke * 0.6f)
    }
}
