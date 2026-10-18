package com.omenight.bright.setup.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.omenight.bright.setup.ui.theme.BrightColors

/** Focus [target] once it is attached, lazy content attaches during layout so wait a frame. */
@Composable
fun RequestFocusOnEnter(target: FocusRequester, key: Any? = null) {
    LaunchedEffect(target, key) {
        withFrameNanos { }
        runCatching { target.requestFocus() }
    }
}

/** Bright's guide button, a pill that turns solid white with dark text when focused. */
@Composable
fun PillButton(text: String, primary: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (primary) 0.18f else 0.08f),
            focusedContainerColor = BrightColors.Text,
            pressedContainerColor = BrightColors.Text.copy(alpha = 0.85f),
            disabledContainerColor = Color.White.copy(alpha = 0.05f),
            contentColor = BrightColors.Text,
            focusedContentColor = BrightColors.OnLight,
            pressedContentColor = BrightColors.OnLight,
            disabledContentColor = BrightColors.TextMuted,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
        )
    }
}

/** Bright's panel row, a full-width line that lights up behind itself when focused. */
@Composable
fun PanelRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            pressedContainerColor = Color.White.copy(alpha = 0.20f),
            disabledContainerColor = Color.Transparent,
            contentColor = BrightColors.Text,
            focusedContentColor = BrightColors.Text,
            disabledContentColor = BrightColors.TextMuted,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(
                text = title,
                style = TextStyle(fontSize = 17.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = if (leading != null) 14.dp else 0.dp)
                    .weight(1f),
            )
            trailing?.invoke()
        }
    }
}

@Composable
fun RowValue(text: String) {
    Text(text = text, color = BrightColors.TextMuted, style = TextStyle(fontSize = 15.sp), maxLines = 1, modifier = Modifier.padding(start = 12.dp))
}

/** The small white dot Bright uses to mark the current choice. */
@Composable
fun SelectedDot() {
    Box(Modifier.size(7.dp).background(BrightColors.Text, CircleShape))
}

/** Progress through the steps, the current one stretched into a pill. */
@Composable
fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == current) 26.dp else 7.dp, tween(260), label = "step")
            Box(
                Modifier
                    .height(7.dp)
                    .width(width)
                    .background(BrightColors.Text.copy(alpha = if (index <= current) 1f else 0.3f), CircleShape),
            )
        }
    }
}

/** A status line under a page title, with a dot that breathes while something is in progress. */
@Composable
fun StatusLine(text: String, busy: Boolean, modifier: Modifier = Modifier, glyph: Glyph? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when {
            glyph != null -> GlyphIcon(glyph, size = 22.dp)
            busy -> PulsingDot()
        }
        Text(
            text = text,
            style = TextStyle(fontSize = 17.sp, shadow = BrightColors.TextShadow),
            modifier = Modifier.padding(start = if (glyph != null || busy) 12.dp else 0.dp),
        )
    }
}

@Composable
fun PulsingDot(modifier: Modifier = Modifier) {
    val breath by rememberInfiniteTransition(label = "pulse")
        .animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(
        modifier
            .size(10.dp)
            .graphicsLayer { alpha = breath }
            .background(BrightColors.Text, CircleShape),
    )
}
