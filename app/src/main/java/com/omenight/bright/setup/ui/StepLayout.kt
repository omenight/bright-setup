package com.omenight.bright.setup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.omenight.bright.setup.ui.theme.BrightColors

const val STEP_COUNT = 3

/** Every step shares one frame, the words on the left and the thing to act on to the right. */
@Composable
fun StepLayout(
    step: Int,
    title: String,
    subtitle: String,
    buttons: @Composable RowScope.() -> Unit,
    status: @Composable ColumnScope.() -> Unit = {},
    side: @Composable BoxScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp)) {
        Column(Modifier.width(370.dp).fillMaxHeight()) {
            StepDots(STEP_COUNT, step)
            Spacer(Modifier.weight(1f))
            Text(
                text = title,
                style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Light, lineHeight = 46.sp, shadow = BrightColors.TextShadow),
            )
            Text(
                text = subtitle,
                color = BrightColors.TextMuted,
                style = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, shadow = BrightColors.TextShadow),
                modifier = Modifier.padding(top = 14.dp),
            )
            Spacer(Modifier.height(26.dp))
            Column(Modifier.height(64.dp), content = status)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = buttons)
        }
        Spacer(Modifier.width(48.dp))
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center, content = side)
    }
}
