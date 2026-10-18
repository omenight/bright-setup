package com.omenight.bright.setup.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omenight.bright.setup.R
import com.omenight.bright.setup.data.SystemSetup

@Composable
fun LanguagePage(onBack: () -> Unit, onNext: () -> Unit) {
    var current by remember { mutableStateOf(SystemSetup.currentLanguage()) }
    val currentRow = remember { FocusRequester() }
    RequestFocusOnEnter(currentRow)

    StepLayout(
        step = 1,
        title = stringResource(R.string.language_title),
        subtitle = stringResource(R.string.language_subtitle),
        buttons = {
            PillButton(stringResource(R.string.back), primary = false, onClick = onBack)
            PillButton(stringResource(R.string.next), primary = true, onClick = onNext)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .surface(30.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .focusGroup(),
        ) {
            SystemSetup.languages.forEach { (locale, name) ->
                val selected = locale.language == current.language
                PanelRow(
                    title = name,
                    // Applied at once, so the rest of setup already speaks the chosen language
                    onClick = {
                        SystemSetup.setLanguage(locale)
                        current = locale
                    },
                    modifier = if (selected) Modifier.focusRequester(currentRow) else Modifier,
                    trailing = if (selected) ({ SelectedDot() }) else null,
                )
            }
        }
    }
}
