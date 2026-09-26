package com.junkfood.seal.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.junkfood.seal.R
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.STATUS_AUTO_DELETE

/** Asks once (first launch) whether to keep videos after a Status share; Settings can change it. */
@Composable
fun AutoDeleteChoiceGate() {
    var show by remember { mutableStateOf(!PreferenceUtil.containsKey(STATUS_AUTO_DELETE)) }
    if (!show) return
    fun choose(autoDelete: Boolean) {
        STATUS_AUTO_DELETE.updateBoolean(autoDelete)
        show = false
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Outlined.AutoDelete, null) },
        title = { Text(stringResource(R.string.auto_delete_title)) },
        text = { Text(stringResource(R.string.auto_delete_desc)) },
        confirmButton = {
            Button(onClick = { choose(true) }) { Text(stringResource(R.string.auto_delete_yes)) }
        },
        dismissButton = {
            OutlinedButton(onClick = { choose(false) }) {
                Text(stringResource(R.string.auto_delete_no))
            }
        },
    )
}
