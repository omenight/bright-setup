package com.omenight.bright.setup.ui

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.omenight.bright.setup.R
import com.omenight.bright.setup.ui.theme.BrightColors

private sealed interface Key {
    val units: Float

    data class Char(val char: kotlin.Char, override val units: Float = 1f) : Key
    data object Shift : Key { override val units = 1.5f }
    data object Backspace : Key { override val units = 1.5f }
    data object Mode : Key { override val units = 1.5f }
    data class Space(override val units: Float) : Key
    data object Done : Key { override val units = 3f }
}

private fun chars(row: String) = row.map { Key.Char(it) }

private val letters = listOf(
    chars("1234567890"),
    chars("qwertyuiop"),
    chars("asdfghjkl"),
    listOf(Key.Shift) + chars("zxcvbnm") + Key.Backspace,
    listOf(Key.Mode, Key.Space(4.5f), Key.Char('.'), Key.Done),
)

private val symbols = listOf(
    chars("1234567890"),
    chars("!@#$%^&*()"),
    chars("-_=+[]{}\\|"),
    chars("`~;:'\",?") + Key.Backspace,
    listOf(Key.Mode, Key.Char('<'), Key.Char('>'), Key.Space(1.5f), Key.Char('/'), Key.Char('.'), Key.Done),
)

private val KeyUnit = 33.dp
private val KeyGap = 4.dp

/**
 * A D-pad keyboard for Wi-Fi passwords, the box's own keyboard cannot be driven from a remote.
 * Typing on a real keyboard or the remote's number keys works too.
 */
@Composable
fun OnScreenKeyboard(
    onType: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    doneEnabled: Boolean,
    firstKey: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var shift by remember { mutableStateOf(false) }
    var symbolMode by remember { mutableStateOf(false) }
    val type = { c: Char ->
        onType(if (shift) c.uppercaseChar() else c)
        shift = false
    }

    Column(
        modifier
            .focusGroup()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val native = event.nativeKeyEvent
                when {
                    native.keyCode == NativeKeyEvent.KEYCODE_DEL -> onBackspace().let { true }
                    native.unicodeChar in 0x21..0x7E && native.keyCode !in dpadKeys -> {
                        onType(native.unicodeChar.toChar())
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(KeyGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        (if (symbolMode) symbols else letters).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                row.forEachIndexed { index, key ->
                    val focus = if (rowIndex == 1 && index == 0) Modifier.focusRequester(firstKey) else Modifier
                    when (key) {
                        is Key.Char -> KeyCap(key.units, focus, onClick = { type(key.char) }) {
                            KeyLabel((if (shift) key.char.uppercaseChar() else key.char).toString())
                        }
                        Key.Shift -> KeyCap(key.units, focus, active = shift, onClick = { shift = !shift }) {
                            GlyphIcon(Glyph.Shift, size = 18.dp)
                        }
                        Key.Backspace -> KeyCap(key.units, focus, onClick = onBackspace) { GlyphIcon(Glyph.Backspace, size = 20.dp) }
                        Key.Mode -> KeyCap(key.units, focus, onClick = { symbolMode = !symbolMode; shift = false }) {
                            KeyLabel(if (symbolMode) "abc" else "#+=", small = true)
                        }
                        is Key.Space -> KeyCap(key.units, focus, onClick = { onType(' ') }) {
                            KeyLabel(stringResource(R.string.key_space), small = true)
                        }
                        Key.Done -> KeyCap(key.units, focus, enabled = doneEnabled, primary = true, onClick = onDone) {
                            KeyLabel(stringResource(R.string.wifi_connect), small = true)
                        }
                    }
                }
            }
        }
    }
}

private val dpadKeys = setOf(
    NativeKeyEvent.KEYCODE_DPAD_UP, NativeKeyEvent.KEYCODE_DPAD_DOWN, NativeKeyEvent.KEYCODE_DPAD_LEFT,
    NativeKeyEvent.KEYCODE_DPAD_RIGHT, NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_ENTER,
)

private fun keyWidth(units: Float): Dp = KeyUnit * units + KeyGap * (units - 1f)

// Same focus cue as Bright's buttons, solid white with dark text
@Composable
private fun KeyCap(
    units: Float,
    modifier: Modifier,
    active: Boolean = false,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.width(keyWidth(units)).height(36.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) BrightColors.Text else Color.White.copy(alpha = if (primary) 0.18f else 0.08f),
            focusedContainerColor = BrightColors.Text,
            pressedContainerColor = BrightColors.Text.copy(alpha = 0.85f),
            disabledContainerColor = Color.White.copy(alpha = 0.04f),
            contentColor = if (active) BrightColors.OnLight else BrightColors.Text,
            focusedContentColor = BrightColors.OnLight,
            pressedContentColor = BrightColors.OnLight,
            disabledContentColor = BrightColors.TextMuted,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.width(keyWidth(units)).height(36.dp), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun KeyLabel(text: String, small: Boolean = false) {
    Text(text = text, style = TextStyle(fontSize = if (small) 14.sp else 17.sp, fontWeight = FontWeight.Medium))
}
