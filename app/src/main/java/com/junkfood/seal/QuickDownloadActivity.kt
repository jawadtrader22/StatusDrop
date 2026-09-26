package com.junkfood.seal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.download.Task.DownloadState.Canceled
import com.junkfood.seal.download.Task.DownloadState.Completed
import com.junkfood.seal.download.Task.DownloadState.Error
import com.junkfood.seal.download.Task.DownloadState.Running
import com.junkfood.seal.ui.common.AsyncImageImpl
import com.junkfood.seal.ui.common.LocalDarkTheme
import com.junkfood.seal.ui.common.SettingsProvider
import com.junkfood.seal.ui.component.AutoDeleteChoiceGate
import com.junkfood.seal.ui.component.DownloadProgress
import com.junkfood.seal.ui.component.PlatformChip
import com.junkfood.seal.ui.theme.SealTheme
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.Platform
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.getErrorReport
import com.junkfood.seal.util.makeToast
import com.junkfood.seal.util.matchUrlFromSharedText
import com.junkfood.seal.util.setLanguage
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/** Share a link here → it downloads straight away → one tap to post it to WhatsApp Status. */
class QuickDownloadActivity : ComponentActivity() {
    private val downloader: DownloaderV2 by inject()

    private fun Intent.getSharedURL(): String? =
        when (action) {
            Intent.ACTION_VIEW -> dataString
            Intent.ACTION_SEND ->
                // kept in the intent so a rotation/theme recreate still finds the link
                getStringExtra(Intent.EXTRA_TEXT)?.let { matchUrlFromSharedText(it) }
            else -> null
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // another link shared while the panel is open: show that one instead
        setIntent(intent)
        recreate()
    }

    /** Shares to WhatsApp and queues the auto-delete; false if the file is gone. */
    private fun shareToStatus(path: String?): Boolean {
        val intent = FileUtil.createIntentForStatusSharing(path) ?: return false
        startActivity(intent)
        FileUtil.deleteLaterIfAutoDelete(path)
        return true
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getSharedURL()
        if (url.isNullOrEmpty()) {
            finish()
            return
        }

        App.startService()
        enableEdgeToEdge()
        window.run {
            setBackgroundDrawable(ColorDrawable(0))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }

        val task = Task(url = url, preferences = DownloadUtil.DownloadPreferences.createFromPreferences())
        val existing = downloader.getTaskStateMap()[task]?.downloadState
        // same link shared twice: keep the running/finished task instead of starting over,
        // unless its file is gone (auto-deleted) and needs downloading again
        val fileGone =
            existing is Completed && FileUtil.createIntentForOpeningFile(existing.filePath) == null
        if (existing == null || existing is Error || existing is Canceled || fileGone) {
            if (existing != null) downloader.remove(task)
            downloader.enqueue(task)
        }

        setContent {
            SettingsProvider(calculateWindowSizeClass(this).widthSizeClass) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                ) {
                    AutoDeleteChoiceGate()
                    QuickSharePanel(
                        task = task,
                        state = downloader.getTaskStateMap()[task],
                        onDismiss = { finish() },
                        onRetry = { downloader.restart(task) },
                        onMoreOptions = {
                            downloader.cancel(task)
                            downloader.remove(task)
                            startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .setClass(this, MainActivity::class.java)
                            )
                            finish()
                        },
                        onShareToStatus = { path ->
                            if (shareToStatus(path)) finish()
                            else makeToast(R.string.file_unavailable)
                        },
                        onShare = { path ->
                            FileUtil.createIntentForSharingFile(path)?.let {
                                startActivity(Intent.createChooser(it, getString(R.string.share)))
                            } ?: makeToast(R.string.file_unavailable)
                        },
                        onOpen = { path ->
                            FileUtil.openFile(path) { makeToast(R.string.file_unavailable) }
                        },
                        onCopyReport = { th ->
                            getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(
                                    ClipData.newPlainText(null, getErrorReport(th, url))
                                )
                            makeToast(R.string.error_copied)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSharePanel(
    task: Task,
    state: Task.State?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onMoreOptions: () -> Unit,
    onShareToStatus: (String?) -> Unit,
    onShare: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onCopyReport: (Throwable) -> Unit,
) {
    val downloadState = state?.downloadState
    val viewState = state?.viewState

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                // tapping outside only hides the panel, the download keeps going
                .clickable(interactionSource = MutableInteractionSource(), indication = null) {
                    onDismiss()
                },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(interactionSource = MutableInteractionSource(), indication = null) {},
            shape = MaterialTheme.shapes.extraLarge.copy(
                bottomStart = MaterialTheme.shapes.extraSmall.bottomStart,
                bottomEnd = MaterialTheme.shapes.extraSmall.bottomEnd,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val thumbModifier =
                        Modifier.width(112.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)
                    if (viewState?.thumbnailUrl != null) {
                        AsyncImageImpl(
                            model = viewState.thumbnailUrl,
                            contentDescription = null,
                            modifier = thumbModifier,
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(thumbModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = viewState?.title?.takeIf { it != task.url } ?: task.url,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PlatformChip(Platform.of(task.url))
                    }
                }

                when (downloadState) {
                    is Completed -> {
                        Text(
                            stringResource(R.string.ready_to_share),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Button(
                            onClick = { onShareToStatus(downloadState.filePath) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Icon(Icons.Rounded.DonutLarge, null, Modifier.size(20.dp))
                            Text(
                                stringResource(R.string.share_to_status),
                                Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { onShare(downloadState.filePath) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.share), Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(
                                onClick = { downloadState.filePath?.let(onOpen) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.open_file), Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    is Error -> {
                        Text(
                            downloadState.throwable.message ?: stringResource(R.string.status_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.resume), Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(
                                onClick = { onCopyReport(downloadState.throwable) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.copy_error_report))
                            }
                        }
                    }
                    is Canceled -> {
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.resume))
                        }
                    }
                    else -> {
                        val running = downloadState as? Running
                        DownloadProgress(
                            progress = running?.progress ?: -1f,
                            progressText = running?.progressText.orEmpty(),
                            label =
                                stringResource(
                                    if (running != null) R.string.status_downloading
                                    else R.string.fetching_info
                                ),
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (downloadState !is Completed) {
                        TextButton(onClick = onMoreOptions) { Text(stringResource(R.string.more_options)) }
                    } else {
                        Box {}
                    }
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(),
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
