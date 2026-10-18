package com.omenight.bright.setup.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt
import kotlin.random.Random

/** Bright's default Dawn wallpaper and the surface color matched to it. */
@Immutable
class Backdrop(val wallpaper: ImageBitmap, val grain: ImageBitmap, val surface: Color)

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private val FallbackSurface = Color(0xFF1B1D24)

/** Bright's one material, an opaque surface in the wallpaper's own hue. */
fun Modifier.surface(cornerRadius: Dp): Modifier = composed {
    background(LocalBackdrop.current?.surface ?: FallbackSurface, RoundedCornerShape(cornerRadius))
}

fun Modifier.backdrop(backdrop: Backdrop?, fallback: Color): Modifier = drawWithCache {
    if (backdrop == null) return@drawWithCache onDrawBehind { drawRect(fallback) }
    val grain = ShaderBrush(ImageShader(backdrop.grain, TileMode.Repeated, TileMode.Repeated))
    val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
    onDrawBehind {
        drawImage(backdrop.wallpaper, dstSize = dst, filterQuality = FilterQuality.Low)
        drawRect(grain)
    }
}

/** Same recipe as Bright's WallpaperBaker, reduced to the one preset setup shows. */
object BackdropBaker {
    private const val WIDTH = 320
    private const val HEIGHT = 180
    private const val OPAQUE = 0xFF shl 24
    private val base = intArrayOf(0x060914, 0x0C1030, 0x22143A)

    private class Glow(val x: Float, val y: Float, val radius: Float, val rgb: Int, val alpha: Float)

    private val glows = listOf(
        Glow(0.10f, 1.10f, 0.70f, 0xFF9A62, 0.60f),
        Glow(0.50f, 1.05f, 0.50f, 0xC2457E, 0.38f),
        Glow(0.38f, 0.20f, 0.45f, 0x3A3DB0, 0.40f),
        Glow(0.95f, 0.10f, 0.55f, 0x1E6E8C, 0.35f),
    )

    fun bake() = Backdrop(
        wallpaper = render().asImageBitmap(),
        grain = grain().asImageBitmap(),
        surface = lerp(Color(OPAQUE or base[1]), Color.White, 0.09f),
    )

    private fun render(): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, HEIGHT.toFloat(),
            base.map { OPAQUE or it }.toIntArray(),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        glows.forEach { glow ->
            val color = ((glow.alpha * 255).toInt() shl 24) or glow.rgb
            paint.shader = RadialGradient(
                glow.x * WIDTH, glow.y * HEIGHT, glow.radius * WIDTH,
                intArrayOf(color, color and 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        }
        return boxBlur(bitmap, radius = 4, passes = 2)
    }

    private fun grain(size: Int = 128): Bitmap {
        val random = Random(seed = 7)
        val pixels = IntArray(size * size) {
            val alpha = random.nextInt(0, 14)
            val shade = if (random.nextBoolean()) 0xFFFFFF else 0x000000
            (alpha shl 24) or shade
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(source: Bitmap, radius: Int, passes: Int): Bitmap {
        val width = source.width
        val height = source.height
        val a = IntArray(width * height)
        val b = IntArray(width * height)
        source.getPixels(a, 0, width, 0, 0, width, height)
        repeat(passes) {
            blurPass(a, b, width, height, radius, horizontal = true)
            blurPass(b, a, width, height, radius, horizontal = false)
        }
        return Bitmap.createBitmap(a, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blurPass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            fun index(i: Int): Int {
                val k = i.coerceIn(0, length - 1)
                return if (horizontal) line * width + k else k * width + line
            }
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -radius..radius) {
                val p = src[index(i)]
                sa += p ushr 24
                sr += p shr 16 and 0xFF
                sg += p shr 8 and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until length) {
                dst[index(i)] = (sa / window shl 24) or (sr / window shl 16) or (sg / window shl 8) or (sb / window)
                val leaving = src[index(i - radius)]
                val entering = src[index(i + radius + 1)]
                sa += (entering ushr 24) - (leaving ushr 24)
                sr += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
                sg += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
                sb += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }
}

@Composable
fun backdropSurface(): Color = LocalBackdrop.current?.surface ?: FallbackSurface
