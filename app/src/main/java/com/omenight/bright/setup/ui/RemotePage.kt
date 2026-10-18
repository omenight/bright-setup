package com.omenight.bright.setup.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Text
import com.omenight.bright.setup.R
import com.omenight.bright.setup.data.RemotePairer
import com.omenight.bright.setup.data.RemoteState
import com.omenight.bright.setup.ui.theme.BrightColors

@Composable
fun RemotePage(onNext: () -> Unit) {
    val context = LocalContext.current
    val pairer = remember { RemotePairer(context) }
    val state by pairer.state.collectAsState()
    val nearby by pairer.nearby.collectAsState()
    var manual by remember { mutableStateOf(false) }

    // Discovery results need location on Android 10, the setup wizard normally has it by default
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { pairer.start() }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) pairer.start() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    DisposableEffect(Unit) { onDispose { pairer.stop() } }

    val paired = state is RemoteState.Paired
    LaunchedEffect(paired) { if (paired) manual = false }
    val next = remember { FocusRequester() }
    RequestFocusOnEnter(next, key = paired)

    StepLayout(
        step = 0,
        title = stringResource(R.string.remote_title),
        subtitle = stringResource(R.string.remote_subtitle),
        status = {
            when (val s = state) {
                RemoteState.Unavailable -> StatusLine(stringResource(R.string.remote_unavailable), busy = false)
                RemoteState.Searching -> StatusLine(stringResource(R.string.remote_searching), busy = true)
                is RemoteState.Pairing -> StatusLine(stringResource(R.string.remote_pairing, s.name), busy = true)
                is RemoteState.Paired -> StatusLine(stringResource(R.string.remote_paired, s.name), busy = false, glyph = Glyph.Check)
                is RemoteState.Failed -> StatusLine(stringResource(R.string.remote_failed, s.name), busy = false)
            }
        },
        buttons = {
            when {
                state is RemoteState.Failed -> PillButton(stringResource(R.string.try_again), primary = false) { pairer.retry() }
                !paired && state != RemoteState.Unavailable -> PillButton(
                    text = stringResource(if (manual) R.string.remote_hint else R.string.remote_other),
                    primary = false,
                ) { manual = !manual }
            }
            PillButton(
                text = stringResource(if (paired) R.string.next else R.string.skip),
                primary = true,
                modifier = Modifier.focusRequester(next),
                onClick = onNext,
            )
        },
    ) {
        if (manual) {
            DeviceList(nearby.map { it.address to it.name }) { pairer.pair(it); manual = false }
        } else {
            RemoteIllustration(highlight = !paired)
        }
    }
}

@Composable
private fun DeviceList(devices: List<Pair<String, String>>, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .surface(30.dp)
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .focusGroup(),
    ) {
        if (devices.isEmpty()) {
            Text(
                text = stringResource(R.string.remote_none_nearby),
                color = BrightColors.TextMuted,
                style = TextStyle(fontSize = 17.sp),
                modifier = Modifier.padding(12.dp),
            )
        }
        devices.forEach { (address, name) ->
            PanelRow(title = name, onClick = { onPick(address) }, trailing = { RowValue(address.takeLast(5)) })
        }
    }
}

/** A remote drawn in Bright's line style, OK and Volume down breathe until the remote pairs. */
@Composable
private fun RemoteIllustration(highlight: Boolean) {
    val glow by rememberInfiniteTransition(label = "keys")
        .animateFloat(0.25f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")
    val surface = backdropSurface()
    Canvas(Modifier.width(150.dp).height(400.dp)) {
        val w = size.width
        val h = size.height
        val body = Size(w * 0.72f, h)
        val left = (w - body.width) / 2
        val corner = CornerRadius(body.width / 2)
        drawRoundRect(surface, topLeft = Offset(left, 0f), size = body, cornerRadius = corner)
        drawRoundRect(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.55f),
                0.45f to Color.White.copy(alpha = 0.06f),
                1f to Color.White.copy(alpha = 0.22f),
                start = Offset(left, 0f),
                end = Offset(left + body.width, h),
            ),
            topLeft = Offset(left + 0.5f, 0.5f),
            size = Size(body.width - 1f, h - 1f),
            cornerRadius = corner,
            style = Stroke(1.dp.toPx()),
        )

        val cx = w / 2
        val line = BrightColors.Text.copy(alpha = 0.55f)
        val thin = Stroke(1.5.dp.toPx())
        val keyGlow = BrightColors.Text.copy(alpha = if (highlight) glow else 0.55f)

        // Power, then the ring with OK in its middle
        drawCircle(line, radius = w * 0.06f, center = Offset(cx, h * 0.07f), style = thin)
        val ringCenter = Offset(cx, h * 0.27f)
        drawCircle(line, radius = w * 0.27f, center = ringCenter, style = thin)
        drawCircle(keyGlow, radius = w * 0.12f, center = ringCenter)

        // Back, home and menu
        listOf(-1, 0, 1).forEach { i -> drawCircle(line, radius = w * 0.055f, center = Offset(cx + i * w * 0.2f, h * 0.45f), style = thin) }

        // Volume rocker on the left, channel on the right, the lower half of volume is Volume down
        rocker(Offset(cx - w * 0.15f, h * 0.53f), w, h, line, thin, lowerFill = keyGlow)
        rocker(Offset(cx + w * 0.15f, h * 0.53f), w, h, line, thin, lowerFill = null)

        // Number pad hint
        for (row in 0..2) for (column in -1..1) {
            drawCircle(line.copy(alpha = 0.3f), radius = w * 0.03f, center = Offset(cx + column * w * 0.17f, h * (0.76f + row * 0.06f)))
        }
    }
}

private fun DrawScope.rocker(top: Offset, w: Float, h: Float, line: Color, stroke: Stroke, lowerFill: Color?) {
    val size = Size(w * 0.16f, h * 0.17f)
    val topLeft = Offset(top.x - size.width / 2, top.y)
    drawRoundRect(line, topLeft = topLeft, size = size, cornerRadius = CornerRadius(size.width / 2), style = stroke)
    val half = Size(size.width - stroke.width * 4, size.height / 2 - stroke.width * 3)
    if (lowerFill != null) {
        drawRoundRect(
            lowerFill,
            topLeft = Offset(topLeft.x + stroke.width * 2, topLeft.y + size.height / 2 + stroke.width),
            size = half,
            cornerRadius = CornerRadius(half.width / 2),
        )
    }
    // The plus and minus marks
    val plus = Offset(top.x, top.y + size.height * 0.25f)
    val minus = Offset(top.x, top.y + size.height * 0.75f)
    val arm = size.width * 0.18f
    drawLine(line, Offset(plus.x - arm, plus.y), Offset(plus.x + arm, plus.y), strokeWidth = stroke.width)
    drawLine(line, Offset(plus.x, plus.y - arm), Offset(plus.x, plus.y + arm), strokeWidth = stroke.width)
    if (lowerFill == null) drawLine(line, Offset(minus.x - arm, minus.y), Offset(minus.x + arm, minus.y), strokeWidth = stroke.width)
}
