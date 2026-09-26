package com.junkfood.seal.ui.page.downloadv2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.ui.component.PlatformChip
import com.junkfood.seal.util.Platform
import com.junkfood.seal.util.findURLsFromString
import com.junkfood.seal.util.makeToast

/** Paste/auto-filled link → platform chip → one-tap download, no sheets. */
@Composable
fun PasteLinkBar(
    knownUrls: Set<String>,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var text by rememberSaveable { mutableStateOf("") }
    // the last clipboard link we offered, so clearing the field doesn't refill it
    var lastOffered by rememberSaveable { mutableStateOf("") }

    fun clipboardUrl(): String? =
        clipboard.getText()?.text?.let { findURLsFromString(it, firstMatchOnly = true).firstOrNull() }

    LaunchedEffect(windowFocused) {
        if (!windowFocused || text.isNotEmpty()) return@LaunchedEffect
        val url = runCatching { clipboardUrl() }.getOrNull() ?: return@LaunchedEffect
        if (url != lastOffered && url !in knownUrls) {
            text = url
            lastOffered = url
        }
    }

    val url = remember(text) { findURLsFromString(text, firstMatchOnly = true).firstOrNull() }

    fun submit() {
        if (url == null) {
            context.makeToast(R.string.unsupported_link)
            return
        }
        onDownload(url)
        text = ""
        focusManager.clearFocus()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.paste_link_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            trailingIcon = {
                if (text.isEmpty()) {
                    IconButton(
                        onClick = { clipboardUrl()?.let { text = it } ?: clipboard.getText()?.let { text = it.text } }
                    ) {
                        Icon(Icons.Outlined.ContentPaste, stringResource(R.string.paste))
                    }
                } else {
                    IconButton(onClick = { text = "" }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.clear))
                    }
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (url != null) PlatformChip(Platform.of(url))
            Spacer(Modifier.weight(1f))
            Button(onClick = { submit() }, enabled = url != null, modifier = Modifier.height(44.dp)) {
                Icon(Icons.Outlined.FileDownload, null)
                Text(stringResource(R.string.download))
            }
        }
    }
}
