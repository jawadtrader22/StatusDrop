package com.junkfood.seal.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// "[download]  46.3% of ~ 250.21MiB at  2.36MiB/s ETA 01:02 (frag 20/58)"
private val YtDlpProgress = Regex("""of\s+~?\s*([\d.]+\s*\w+)\s+at\s+([\d.]+\s*\w+/s)\s+ETA\s+([\d:]+)""")

/** "250.21MiB · 2.36MiB/s · 01:02 left", or null when yt-dlp hasn't reported yet. */
fun ytDlpProgressDetails(progressText: String): String? =
    YtDlpProgress.find(progressText)?.destructured?.let { (size, speed, eta) ->
        "$size · $speed · $eta left"
    }

/** Filling bar with live %; indeterminate while [progress] < 0 (fetching / not started). */
@Composable
fun DownloadProgress(
    progress: Float,
    progressText: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (progress >= 0f) {
            LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (progress >= 0f) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ytDlpProgressDetails(progressText)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
