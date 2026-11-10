package com.junkfood.seal.ui.page.status

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.junkfood.seal.App
import com.junkfood.seal.R
import com.junkfood.seal.ui.common.AsyncImageImpl
import com.junkfood.seal.ui.component.BackButton
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.makeToast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A status file WhatsApp keeps for 24 h; [path] is a SAF document URI or (Android 10-) a file. */
data class StatusItem(val name: String, val path: String, val isVideo: Boolean, val modified: Long) {
    val uri: Uri
        get() = if (path.startsWith("content:")) Uri.parse(path) else Uri.fromFile(File(path))
}

/**
 * Reads the statuses WhatsApp caches after the user watches them. Android 11+ hides that folder
 * from plain file access, so the user grants it once through the system folder picker.
 * ponytail: tied to WhatsApp's `.Statuses` folder layout; update the paths if WhatsApp moves it.
 */
object StatusFolders {
    data class WhatsApp(val pkg: String, val label: String, val folder: String) {
        val docId = "primary:Android/media/$pkg/$folder/Media/.Statuses"
    }

    val apps =
        listOf(
            WhatsApp("com.whatsapp", "WhatsApp", "WhatsApp"),
            WhatsApp("com.whatsapp.w4b", "WhatsApp Business", "WhatsApp Business"),
        )

    val usesPicker = Build.VERSION.SDK_INT >= 30
    private const val AUTHORITY = "com.android.externalstorage.documents"
    private val videoExt = setOf("mp4", "3gp", "mkv", "webm")
    private val imageExt = setOf("jpg", "jpeg", "png", "webp")

    fun initialUri(app: WhatsApp): Uri = DocumentsContract.buildDocumentUri(AUTHORITY, app.docId)

    fun installed(context: Context) =
        apps.filter { context.packageManager.getLaunchIntentForPackage(it.pkg) != null }

    /** Folder grants we hold; anything else the user picks is rejected in [accept]. */
    fun grants(context: Context): List<Uri> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && isStatusTree(it.uri) }
            .map { it.uri }

    fun granted(context: Context, app: WhatsApp) =
        grants(context).any { "/${app.pkg}/" in DocumentsContract.getTreeDocumentId(it) }

    private fun isStatusTree(uri: Uri) =
        runCatching { DocumentsContract.getTreeDocumentId(uri).endsWith("/.Statuses") }
            .getOrDefault(false)

    /** Keeps the grant only when the user picked a WhatsApp `.Statuses` folder. */
    fun accept(context: Context, uri: Uri): Boolean {
        if (!isStatusTree(uri)) return false
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        return true
    }

    private fun item(name: String, path: String, modified: Long): StatusItem? =
        when (name.substringAfterLast('.').lowercase()) {
            in videoExt -> StatusItem(name, path, true, modified)
            in imageExt -> StatusItem(name, path, false, modified)
            else -> null
        }

    fun load(context: Context): List<StatusItem> =
        (if (usesPicker) grants(context).flatMap { listTree(context, it) } else listLegacy())
            .sortedByDescending { it.modified }

    private fun listTree(context: Context, tree: Uri): List<StatusItem> =
        runCatching {
                val children =
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        tree,
                        DocumentsContract.getTreeDocumentId(tree),
                    )
                val columns =
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    )
                context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            val name = c.getString(1) ?: continue
                            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                            item(name, doc.toString(), c.getLong(2))?.let(::add)
                        }
                    }
                }
            }
            .getOrNull()
            .orEmpty()

    @Suppress("DEPRECATION")
    private fun listLegacy(): List<StatusItem> {
        val root = Environment.getExternalStorageDirectory()
        return apps
            .flatMap {
                listOf(
                    File(root, "Android/media/${it.pkg}/${it.folder}/Media/.Statuses"),
                    File(root, "${it.folder}/Media/.Statuses"),
                )
            }
            .flatMap { it.listFiles()?.toList().orEmpty() }
            .mapNotNull { item(it.name, it.absolutePath, it.lastModified()) }
    }

    fun savedFile(item: StatusItem) = File(App.videoDownloadDir, item.name)

    /** Copies the status into the download folder so it outlives WhatsApp's 24 h. */
    fun save(context: Context, item: StatusItem): Boolean =
        runCatching {
                val dest = savedFile(item)
                if (!dest.exists()) {
                    dest.parentFile?.mkdirs()
                    val tmp = File(dest.parentFile, "${dest.name}.part")
                    context.contentResolver.openInputStream(item.uri)!!.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    check(tmp.renameTo(dest))
                    MediaScannerConnection.scanFile(context, arrayOf(dest.path), null, null)
                }
            }
            .isSuccess

    private val frames = LruCache<String, Bitmap>(48)

    fun cachedFrame(item: StatusItem): Bitmap? = frames[item.path]

    fun videoFrame(context: Context, item: StatusItem): Bitmap? =
        runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, item.uri)
                    if (Build.VERSION.SDK_INT >= 27)
                        retriever.getScaledFrameAtTime(
                            0,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            360,
                            640,
                        )
                    else retriever.getFrameAtTime(0)
                } finally {
                    retriever.release()
                }
            }
            .getOrNull()
            ?.also { frames.put(item.path, it) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun StatusesPage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<StatusItem>?>(null) }
    var saved by remember { mutableStateOf(emptySet<String>()) }
    // bumped after a folder grant so access state is read again
    var grantVersion by remember { mutableStateOf(0) }

    fun refresh() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { StatusFolders.load(context) }
            saved =
                withContext(Dispatchers.IO) {
                    loaded.filter { StatusFolders.savedFile(it).exists() }.map { it.path }.toSet()
                }
            items = loaded
        }
    }

    // back from WhatsApp after watching new statuses: show them
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val storagePermission =
        rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) { refresh() }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (StatusFolders.accept(context, uri)) {
                grantVersion++
                refresh()
            } else context.makeToast(R.string.statuses_wrong_folder)
        }

    val installed = remember { StatusFolders.installed(context) }
    val missing =
        remember(grantVersion) {
            if (!StatusFolders.usesPicker) emptyList()
            else
                (installed.ifEmpty { StatusFolders.apps.take(1) }).filterNot {
                    StatusFolders.granted(context, it)
                }
        }
    val hasAccess =
        if (StatusFolders.usesPicker) remember(grantVersion) { StatusFolders.grants(context).isNotEmpty() }
        else storagePermission.status.isGranted

    fun requestAccess(app: StatusFolders.WhatsApp?) {
        if (StatusFolders.usesPicker && app != null) {
            context.makeToast(R.string.statuses_pick_hint)
            picker.launch(StatusFolders.initialUri(app))
        } else storagePermission.launchPermissionRequest()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.whatsapp_statuses)) },
                navigationIcon = { BackButton(onNavigateBack) },
                actions = {
                    if (hasAccess)
                        IconButton(onClick = { refresh() }) {
                            Icon(Icons.Outlined.Refresh, stringResource(R.string.statuses_refresh))
                        }
                },
            )
        }
    ) { padding ->
        val list = items
        when {
            !hasAccess ->
                AccessPrompt(
                    apps = missing,
                    modifier = Modifier.padding(padding),
                    onAllow = { requestAccess(it) },
                )
            list == null -> Unit
            else ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (list.isEmpty())
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.statuses_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 48.dp),
                            )
                        }
                    items(list, key = { it.path }) { item ->
                        StatusTile(
                            item = item,
                            isSaved = item.path in saved,
                            onOpen = {
                                FileUtil.openFile(item.path) {
                                    context.makeToast(R.string.file_unavailable)
                                }
                            },
                            onSave = {
                                scope.launch {
                                    val ok =
                                        withContext(Dispatchers.IO) {
                                            StatusFolders.save(context, item)
                                        }
                                    if (ok) saved = saved + item.path
                                    context.makeToast(
                                        if (ok) R.string.status_saved else R.string.file_unavailable
                                    )
                                }
                            },
                            onPost = { share(context, FileUtil.createIntentForStatusSharing(item.path)) },
                            onSend = {
                                share(
                                    context,
                                    FileUtil.createIntentForSharingFile(item.path)?.let {
                                        Intent.createChooser(it, null)
                                    },
                                )
                            },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            missing.forEach { app ->
                                TextButton(onClick = { requestAccess(app) }) {
                                    Text(stringResource(R.string.statuses_allow_app, app.label))
                                }
                            }
                            Text(
                                stringResource(R.string.statuses_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
        }
    }
}

private fun share(context: Context, intent: Intent?) {
    if (intent == null) return context.makeToast(R.string.file_unavailable)
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun AccessPrompt(
    apps: List<StatusFolders.WhatsApp>,
    modifier: Modifier = Modifier,
    onAllow: (StatusFolders.WhatsApp?) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.DonutLarge,
            null,
            Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.statuses_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 20.dp),
        )
        if (apps.isEmpty()) Button(onClick = { onAllow(null) }) { Text(stringResource(R.string.statuses_allow)) }
        apps.forEachIndexed { i, app ->
            val label = stringResource(R.string.statuses_allow_app, app.label)
            if (i == 0) Button(onClick = { onAllow(app) }) { Text(label) }
            else FilledTonalButton(onClick = { onAllow(app) }) { Text(label) }
        }
        Text(
            stringResource(R.string.statuses_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun StatusTile(
    item: StatusItem,
    isSaved: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onPost: () -> Unit,
    onSend: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(Color.Black)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            if (item.isVideo) {
                val frame by
                    produceState(StatusFolders.cachedFrame(item), item.path) {
                        if (value == null)
                            value = withContext(Dispatchers.IO) { StatusFolders.videoFrame(context, item) }
                    }
                frame?.let {
                    Image(
                        it.asImageBitmap(),
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Icon(
                    Icons.Rounded.PlayArrow,
                    null,
                    Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    tint = Color.White,
                )
            } else
                AsyncImageImpl(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            if (isSaved)
                Icon(
                    Icons.Rounded.CheckCircle,
                    stringResource(R.string.status_saved),
                    Modifier.align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    tint = Color.White,
                )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TileAction(Icons.Outlined.Download, R.string.status_save, onSave)
            TileAction(Icons.Rounded.DonutLarge, R.string.status_post, onPost)
            TileAction(Icons.Rounded.Share, R.string.status_send, onSend)
        }
    }
}

@Composable
private fun TileAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}
