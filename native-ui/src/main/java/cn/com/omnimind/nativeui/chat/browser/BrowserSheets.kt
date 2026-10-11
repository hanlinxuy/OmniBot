package cn.com.omnimind.nativeui.chat.browser

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.components.OmniIcon
import cn.com.omnimind.nativeui.components.OmniIconButton
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/**
 * The browser's sheets. They read [session] on every recomposition, so a
 * download's progress and a toggled bookmark update while the sheet is open;
 * Dart captured the snapshot when the sheet opened and showed it frozen.
 */
@Composable
fun BrowserSheets(
    sheet: BrowserSheet?,
    session: BrowserSession,
    actions: BrowserActions,
    onSheet: (BrowserSheet?) -> Unit,
) {
    val dismiss = { onSheet(null) }
    OverlayBottomSheet(show = sheet == BrowserSheet.Tabs, title = stringResource(R.string.omni_br_tabs), onDismissRequest = dismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(session.tabs, key = { it.id }) { tab ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) { actions.selectTab(tab.id); dismiss() }
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(tab.active, onClick = null)
                    Spacer(Modifier.width(10.dp))
                    LinkText(tab.title.ifBlank { tab.url }, tab.url, Modifier.weight(1f))
                    OmniIconButton(R.drawable.omni_x, stringResource(R.string.omni_br_close_tab), { actions.closeTab(tab.id) },
                        size = 16.dp, tint = LocalOmniPalette.current.tertiaryText)
                }
            }
        }
        TextButton(stringResource(R.string.omni_br_new_tab), { actions.newTab(); dismiss() }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
    OverlayBottomSheet(show = sheet == BrowserSheet.Menu, title = stringResource(R.string.omni_br_menu), onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            MenuRow(R.drawable.omni_history, R.string.omni_br_history) { onSheet(BrowserSheet.History) }
            MenuRow(R.drawable.omni_star, R.string.omni_br_bookmarks) { onSheet(BrowserSheet.Bookmarks) }
            MenuRow(R.drawable.omni_hard_drive_download, R.string.omni_br_downloads) { onSheet(BrowserSheet.Downloads) }
            MenuRow(R.drawable.omni_monitor, if (session.desktopMode) R.string.omni_br_mobile else R.string.omni_br_desktop) {
                actions.toggleDesktopMode(); dismiss()
            }
            session.tabs.firstOrNull { it.active }?.let { active ->
                MenuRow(R.drawable.omni_x, R.string.omni_br_close_tab) { actions.closeTab(active.id); dismiss() }
            }
        }
    }
    OverlayBottomSheet(show = sheet == BrowserSheet.History, title = stringResource(R.string.omni_br_history), onDismissRequest = dismiss) {
        LinkList(session.history, stringResource(R.string.omni_br_history_empty), onOpen = { actions.navigate(it); dismiss() })
        if (session.history.isNotEmpty()) TextButton(stringResource(R.string.omni_br_clear_history), actions.clearHistory,
            Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
    OverlayBottomSheet(show = sheet == BrowserSheet.Bookmarks, title = stringResource(R.string.omni_br_bookmarks), onDismissRequest = dismiss) {
        LinkList(session.bookmarks, stringResource(R.string.omni_br_bookmarks_empty), onOpen = { actions.navigate(it); dismiss() },
            onRemove = actions.removeBookmark)
    }
    OverlayBottomSheet(show = sheet == BrowserSheet.Downloads, title = stringResource(R.string.omni_br_downloads), onDismissRequest = dismiss) {
        Downloads(session.downloads, actions)
    }
}

@Composable
private fun MenuRow(@DrawableRes icon: Int, label: Int, onClick: () -> Unit) {
    val palette = LocalOmniPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OmniIcon(icon, size = 18.dp, tint = palette.text)
        Spacer(Modifier.width(14.dp))
        Text(stringResource(label), fontSize = 14.sp, color = palette.text)
    }
}

@Composable
private fun LinkText(title: String, url: String, modifier: Modifier) {
    val palette = LocalOmniPalette.current
    Column(modifier) {
        Text(title, fontSize = 14.sp, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (url != title) Text(url, fontSize = 11.sp, color = palette.tertiaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LinkList(links: List<BrowserLink>, empty: String, onOpen: (String) -> Unit, onRemove: ((String) -> Unit)? = null) {
    val palette = LocalOmniPalette.current
    if (links.isEmpty()) {
        Text(empty, fontSize = 13.sp, color = palette.secondaryText, modifier = Modifier.padding(vertical = 28.dp))
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        items(links, key = { it.url }) { link ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onOpen(link.url) }
                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LinkText(link.title.ifBlank { link.url }, link.url, Modifier.weight(1f))
                if (onRemove != null) OmniIconButton(R.drawable.omni_trash_2, stringResource(R.string.omni_br_dl_delete),
                    { onRemove(link.url) }, size = 16.dp, tint = palette.tertiaryText)
            }
        }
    }
}

@Composable
private fun Downloads(downloads: List<BrowserDownload>, actions: BrowserActions) {
    val palette = LocalOmniPalette.current
    if (downloads.isEmpty()) {
        Text(stringResource(R.string.omni_br_downloads_empty), fontSize = 13.sp, color = palette.secondaryText,
            modifier = Modifier.padding(vertical = 28.dp))
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(downloads, key = { it.id }) { item ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.fileName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(downloadStatus(item.status), fontSize = 12.sp, color = palette.secondaryText)
                }
                item.progress?.takeIf { item.canPause }?.let { progress ->
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = progress, height = 4.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = palette.accent,
                            backgroundColor = palette.border))
                }
                item.error?.let { Text(it, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFFE5484D)) }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (item.canPause) Chip(R.string.omni_br_dl_pause) { actions.download(item.id, DownloadAction.Pause) }
                    if (item.canResume) Chip(R.string.omni_br_dl_resume) { actions.download(item.id, DownloadAction.Resume) }
                    if (item.canCancel) Chip(R.string.omni_br_dl_cancel) { actions.download(item.id, DownloadAction.Cancel) }
                    if (item.canRetry) Chip(R.string.omni_br_dl_retry) { actions.download(item.id, DownloadAction.Retry) }
                    if (item.canOpen) Chip(R.string.omni_br_dl_open, primary = true) { actions.download(item.id, DownloadAction.Open) }
                    Chip(R.string.omni_br_dl_delete) { actions.download(item.id, DownloadAction.Delete) }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: Int, primary: Boolean = false, onClick: () -> Unit) {
    TextButton(stringResource(label), onClick, minHeight = 34.dp, minWidth = 56.dp,
        colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
}

/** The engine's status words, readable (Dart printed them raw). */
@Composable
private fun downloadStatus(status: String): String = when (status.lowercase()) {
    "queued", "pending" -> stringResource(R.string.omni_br_dl_queued)
    "downloading", "running" -> stringResource(R.string.omni_br_dl_running)
    "paused" -> stringResource(R.string.omni_br_dl_paused)
    "completed", "done" -> stringResource(R.string.omni_br_dl_done)
    "failed", "error" -> stringResource(R.string.omni_br_dl_failed)
    "canceled", "cancelled" -> stringResource(R.string.omni_br_dl_canceled)
    else -> status
}
