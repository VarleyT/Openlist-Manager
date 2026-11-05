package com.bytegrep.oplist_manager.feature.files

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.bytegrep.oplist_manager.core.common.formatBytes
import com.bytegrep.oplist_manager.core.common.formatDateTime
import com.bytegrep.oplist_manager.core.designsystem.component.ErrorState
import com.bytegrep.oplist_manager.core.designsystem.component.GroupCard
import com.bytegrep.oplist_manager.core.designsystem.component.GroupRow
import com.bytegrep.oplist_manager.core.designsystem.component.LargeTitleBar
import com.bytegrep.oplist_manager.core.designsystem.component.LoadingState
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.SectionHeader
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.FsGetData

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private val PreviewHeight = 420.dp

@Composable
fun FilePreviewScreen(onBack: () -> Unit) {
    val vm: FilePreviewViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize().background(OpenListTheme.colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            LargeTitleBar(title = state.fileName, onBack = onBack)
            val file = state.file
            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(message = state.error.orEmpty(), onRetry = vm::load)
                file != null -> PreviewBody(
                    state = state,
                    file = file,
                    onOpenWith = { vm.notify(openWithOtherApp(context, file.rawUrl, state.mimeType)) },
                    onDownload = vm::download,
                    onCopyLink = { vm.notify(copyLink(context, file.rawUrl)) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Spacing.lg))
    }
}

@Composable
private fun PreviewBody(
    state: FilePreviewUiState,
    file: FsGetData,
    onOpenWith: () -> Unit,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page)
            .padding(bottom = Spacing.xxl),
    ) {
        val text = state.text
        when {
            text != null -> TextPreviewCard(text)
            state.kind == FileKind.IMAGE && file.rawUrl.isNotBlank() -> ZoomableImage(file.rawUrl, file.name)
            else -> GenericPreviewCard(state.kind, file)
        }
        Spacer(Modifier.height(Spacing.lg))
        PreviewActions(onOpenWith = onOpenWith, onDownload = onDownload, onCopyLink = onCopyLink)
        SectionHeader("文件信息")
        FileInfoCard(state.path, file)
    }
}

/** 双指缩放 + 平移的图片 */
@Composable
private fun ZoomableImage(url: String, name: String) {
    var scale by remember { mutableFloatStateOf(MIN_ZOOM) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        offset = if (scale <= MIN_ZOOM) Offset.Zero else offset + panChange
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .clip(RoundedCornerShape(Radius.card))
            .background(OpenListTheme.colors.surface)
            .transformable(transform),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}

@Composable
private fun TextPreviewCard(text: TextPreview) {
    OlCard {
        Box(Modifier.fillMaxWidth().height(PreviewHeight), contentAlignment = Alignment.TopStart) {
            when (text) {
                TextPreview.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 3.dp)
                is TextPreview.Error -> Text(
                    "无法加载文本：${text.message}",
                    color = OpenListTheme.colors.danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(Spacing.lg),
                )
                is TextPreview.Content -> TextPreviewLines(text.text)
            }
        }
    }
}

/** 按行懒加载渲染：只排版可见行，大文件不再一次性阻塞主线程 */
@Composable
private fun TextPreviewLines(text: String) {
    val lines = remember(text) { if (text.isEmpty()) listOf("（空文件）") else splitPreviewLines(text) }
    Box(
        Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState()),
    ) {
        LazyColumn(Modifier.fillMaxHeight(), contentPadding = PaddingValues(Spacing.md)) {
            items(lines) { line ->
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpenListTheme.colors.ink,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun GenericPreviewCard(kind: FileKind, file: FsGetData) {
    OlCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.xxl, horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FileIcon(kind = kind, thumb = file.thumb, size = 96.dp, iconSize = 52.dp)
            Spacer(Modifier.height(Spacing.lg))
            Text(file.name, style = MaterialTheme.typography.titleMedium, color = OpenListTheme.colors.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (file.isDir) "文件夹" else formatBytes(file.size),
                style = MaterialTheme.typography.bodySmall,
                color = OpenListTheme.colors.ink2,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text("此类型暂不支持应用内预览，可使用其他应用打开", style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink3, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PreviewActions(onOpenWith: () -> Unit, onDownload: () -> Unit, onCopyLink: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Button(onClick = onOpenWith, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.button)) {
            ButtonContent(Icons.AutoMirrored.Outlined.OpenInNew, "用其他应用打开")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = onDownload, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radius.button)) {
                ButtonContent(Icons.Outlined.Download, "下载到本机")
            }
            OutlinedButton(onClick = onCopyLink, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radius.button)) {
                ButtonContent(Icons.Outlined.Link, "复制链接")
            }
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.size(Spacing.sm))
    Text(label)
}

@Composable
private fun FileInfoCard(path: String, file: FsGetData) {
    GroupCard(
        rows = listOf(
            { InfoRow("路径", path) },
            { InfoRow("大小", if (file.isDir) "—" else formatBytes(file.size)) },
            { InfoRow("修改时间", formatDateTime(file.modified).ifEmpty { "—" }) },
            { InfoRow("存储驱动", file.provider.ifBlank { "—" }) },
        ),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    GroupRow(title = label, trailingText = value, showChevron = false)
}

/** 使用外部应用打开，返回提示文案（成功时为空串不提示） */
private fun openWithOtherApp(context: Context, rawUrl: String, mime: String?): String {
    if (rawUrl.isBlank()) return "该文件没有可用的访问链接"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(rawUrl), mime ?: "*/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(Intent.createChooser(intent, "选择打开方式").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ""
    } catch (_: ActivityNotFoundException) {
        "没有可以打开此文件的应用"
    }
}

private fun copyLink(context: Context, rawUrl: String): String {
    if (rawUrl.isBlank()) return "该文件没有可用的访问链接"
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return "剪贴板不可用"
    clipboard.setPrimaryClip(ClipData.newPlainText("OpenList 链接", rawUrl))
    return "链接已复制"
}
