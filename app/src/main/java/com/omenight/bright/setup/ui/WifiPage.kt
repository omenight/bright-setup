package com.omenight.bright.setup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.omenight.bright.setup.R
import com.omenight.bright.setup.data.Joining
import com.omenight.bright.setup.data.Link
import com.omenight.bright.setup.data.Security
import com.omenight.bright.setup.data.WifiNetwork
import com.omenight.bright.setup.data.WifiSetup
import com.omenight.bright.setup.ui.theme.BrightColors

private const val MAX_PASSWORD = 63

@Composable
fun WifiPage(onBack: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val wifi = remember { WifiSetup(context) }
    DisposableEffect(Unit) {
        wifi.start()
        onDispose { wifi.stop() }
    }
    val networks by wifi.networks.collectAsState()
    val link by wifi.link.collectAsState()
    val joining by wifi.joining.collectAsState()

    // The network whose password is being typed, null while the list shows
    var entering by remember { mutableStateOf<WifiNetwork?>(null) }
    var password by remember { mutableStateOf("") }
    BackHandler(enabled = entering != null) {
        entering = null
        wifi.clearJoining()
    }
    // Joined, so the password view has done its job
    LaunchedEffect(link) {
        if (link is Link.Wifi && (link as Link.Wifi).ssid == entering?.ssid) entering = null
    }

    val online = link != Link.None
    val finish = remember { FocusRequester() }
    RequestFocusOnEnter(finish, key = link)

    StepLayout(
        step = 2,
        title = stringResource(R.string.wifi_title),
        subtitle = when (val l = link) {
            Link.Ethernet -> stringResource(R.string.wifi_ethernet)
            is Link.Wifi -> l.ssid?.let { stringResource(R.string.wifi_connected, it) } ?: stringResource(R.string.wifi_connected_unnamed)
            Link.None -> stringResource(R.string.wifi_choose)
        },
        status = {
            when (val j = joining) {
                is Joining.Connecting -> StatusLine(stringResource(R.string.wifi_connecting, j.ssid), busy = true)
                is Joining.WrongPassword -> StatusLine(stringResource(R.string.wifi_wrong_password, j.ssid), busy = false)
                is Joining.Failed -> StatusLine(stringResource(R.string.wifi_failed, j.ssid), busy = false)
                Joining.Idle -> if (online) StatusLine(stringResource(R.string.time_synced), busy = false, glyph = Glyph.Check)
            }
        },
        buttons = {
            PillButton(stringResource(R.string.back), primary = false) {
                if (entering != null) entering = null else onBack()
            }
            PillButton(
                text = stringResource(if (online) R.string.finish else R.string.skip),
                primary = true,
                modifier = Modifier.focusRequester(finish),
                onClick = onFinish,
            )
        },
    ) {
        val network = entering
        if (network == null) {
            NetworkList(
                networks = networks,
                connected = (link as? Link.Wifi)?.ssid,
                focusFirst = !online,
                onPick = { picked ->
                    wifi.clearJoining()
                    if (picked.security == Security.Open) {
                        wifi.join(picked, "")
                    } else {
                        password = ""
                        entering = picked
                    }
                },
            )
        } else {
            PasswordPanel(
                network = network,
                password = password,
                connecting = joining is Joining.Connecting,
                onChange = { password = it.take(MAX_PASSWORD) },
                onConnect = { wifi.join(network, password) },
            )
        }
    }
}

@Composable
private fun NetworkList(
    networks: List<WifiNetwork>,
    connected: String?,
    focusFirst: Boolean,
    onPick: (WifiNetwork) -> Unit,
) {
    val first = remember { FocusRequester() }
    if (focusFirst && networks.isNotEmpty()) RequestFocusOnEnter(first)

    Box(
        Modifier
            .fillMaxWidth()
            .surface(30.dp)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        if (networks.isEmpty()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PulsingDot()
                Text(
                    text = stringResource(R.string.wifi_searching),
                    color = BrightColors.TextMuted,
                    style = TextStyle(fontSize = 17.sp),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        LazyColumn(Modifier.heightIn(max = 400.dp).focusGroup()) {
            items(networks, key = { it.ssid }) { network ->
                val isConnected = network.ssid == connected
                val supported = network.security != Security.Unsupported
                PanelRow(
                    title = network.ssid,
                    enabled = supported && !isConnected,
                    onClick = { onPick(network) },
                    modifier = if (network == networks.first()) Modifier.focusRequester(first) else Modifier,
                    leading = { WifiGlyph(network.bars, size = 24.dp) },
                    trailing = {
                        when {
                            isConnected -> RowValue(stringResource(R.string.wifi_connected_value))
                            !supported -> RowValue(stringResource(R.string.wifi_unsupported))
                            network.security != Security.Open -> GlyphIcon(Glyph.Lock, size = 18.dp, color = BrightColors.TextMuted)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PasswordPanel(
    network: WifiNetwork,
    password: String,
    connecting: Boolean,
    onChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val firstKey = remember { FocusRequester() }
    RequestFocusOnEnter(firstKey, key = network.ssid)

    Column(
        Modifier
            .surface(30.dp)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WifiGlyph(network.bars, size = 24.dp)
            Text(
                text = network.ssid,
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Light),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        PasswordField(password)
        Spacer(Modifier.height(14.dp))
        OnScreenKeyboard(
            onType = { onChange(password + it) },
            onBackspace = { onChange(password.dropLast(1)) },
            onDone = onConnect,
            // WPA needs at least 8 characters
            doneEnabled = password.length >= 8 && !connecting,
            firstKey = firstKey,
        )
    }
}

// Shown in the clear, typing with a remote is slow enough without guessing at hidden characters
@Composable
private fun PasswordField(password: String) {
    val caret by rememberInfiniteTransition(label = "caret")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "blink")
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (password.isEmpty()) {
            Text(stringResource(R.string.wifi_password), color = BrightColors.TextMuted, style = TextStyle(fontSize = 18.sp))
        } else {
            Text(
                text = password,
                style = TextStyle(fontSize = 18.sp, letterSpacing = 0.5.sp),
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Box(
            Modifier
                .padding(start = 2.dp)
                .width(2.dp)
                .height(22.dp)
                .graphicsLayer { alpha = if (caret > 0.5f) 1f else 0f }
                .background(BrightColors.Text),
        )
    }
}
