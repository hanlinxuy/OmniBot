package cn.com.omnimind.nativeui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import cn.com.omnimind.nativeui.ConversationSummary
import cn.com.omnimind.nativeui.components.OmniConfirmDialog
import cn.com.omnimind.nativeui.components.OmniDialogActions
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.LegacyDestination
import cn.com.omnimind.nativeui.NativeHomeActions
import cn.com.omnimind.nativeui.NativeHomeState
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.components.OmniIcon
import cn.com.omnimind.nativeui.components.OmniIconButton
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.compose.ui.platform.LocalConfiguration

@Composable
internal fun DrawerConversationList(
    state: NativeHomeState,
    query: String,
    actions: NativeHomeActions,
    modifier: Modifier = Modifier,
    archivedOnly: Boolean = false,
) {
    val palette = LocalOmniPalette.current
    // Re-evaluate local dates when the drawer is revisited/refreshed; no per-row timer.
    // Message hits belong to one query; an older search never filters a newer one.
    val messageHits = state.searchHits?.takeIf { it.query == query }?.hits
    val rows = remember(state.conversations, state.scheduledTasks, state.expandedSections, query, archivedOnly,
        state.recentConversationsOnly, messageHits) {
        projectDrawerRows(state.conversations, state.scheduledTasks, state.expandedSections, query, archivedOnly,
            state.recentConversationsOnly, messageHits = messageHits)
    }
    val searching = DrawerSearch.tokens(query).isNotEmpty() && messageHits == null
    var menuKey by rememberSaveable { mutableStateOf<String?>(null) }
    var renameKey by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteKey by rememberSaveable { mutableStateOf<String?>(null) }
    fun find(key: String?) = key?.let { k -> state.conversations.firstOrNull { it.key == k } }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
        if (state.error != null) item(key = "error") {
            Text(state.error, fontSize = 12.sp, color = palette.secondaryText)
            TextButton(stringResource(R.string.omni_retry), actions.refresh)
        }
        if (searching) item(key = "searching") {
            Text(stringResource(R.string.omni_dr_searching), fontSize = 12.sp, color = palette.tertiaryText,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        if (!state.loading && !searching && rows.isEmpty()) item(key = "empty") {
            Text(stringResource(if (query.isBlank()) R.string.omni_no_conversations else R.string.omni_no_search_results),
                fontSize = 13.sp, color = palette.secondaryText, modifier = Modifier.padding(vertical = 24.dp))
        }
        items(rows, key = { it.key }, contentType = { if (it is DrawerRow.Heading) "heading" else "thread" }) { row ->
            when (row) {
                is DrawerRow.Heading -> DrawerSectionHeading(row) { actions.setSectionExpanded(row.key, !row.expanded) }
                is DrawerRow.Thread -> ConversationRow(row, state, actions, if (archivedOnly) palette.page else palette.drawer,
                    showArchived = query.isNotBlank() && !archivedOnly) { menuKey = row.conversation.key }
            }
        }
    }
    ConversationMenu(find(menuKey), state, actions, onDismiss = { menuKey = null },
        onRename = { renameKey = it.key }, onDelete = { deleteKey = it.key })
    RenameDialog(find(renameKey), onDismiss = { renameKey = null }) { conversation, title -> actions.rename(conversation, title) }
    val deleting = find(deleteKey)
    OmniConfirmDialog(
        show = deleting != null,
        title = stringResource(R.string.omni_dr_delete),
        summary = stringResource(R.string.omni_dr_delete_body, deleting?.title.orEmpty()),
        confirmText = stringResource(R.string.omni_agent_delete),
        onConfirm = {
            deleting?.let(actions.delete)
            deleteKey = null
        },
        onDismiss = { deleteKey = null },
    )
}

/** Long-press actions for one conversation (Dart swipe actions and title editing). */
@Composable
private fun ConversationMenu(
    conversation: ConversationSummary?,
    state: NativeHomeState,
    actions: NativeHomeActions,
    onDismiss: () -> Unit,
    onRename: (ConversationSummary) -> Unit,
    onDelete: (ConversationSummary) -> Unit,
) {
    val shown = rememberLastConversation(conversation) ?: return
    val busy = shown.id in state.busyConversationIds
    OverlayBottomSheet(show = conversation != null, title = shown.title, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            fun run(action: () -> Unit) = { onDismiss(); action() }
            MenuRow(R.drawable.omni_message_square, stringResource(R.string.omni_transcript_open), true,
                run { actions.previewTranscript(shown) })
            MenuRow(if (shown.pinned) R.drawable.omni_pin_off else R.drawable.omni_pin,
                stringResource(if (shown.pinned) R.string.omni_dr_unpin else R.string.omni_dr_pin), !busy,
                run { actions.setPinned(shown, !shown.pinned) })
            MenuRow(R.drawable.omni_pencil, stringResource(R.string.omni_dr_rename), !busy, run { onRename(shown) })
            MenuRow(R.drawable.omni_copy, stringResource(R.string.omni_dr_copy), !busy, run { actions.copy(shown) })
            MenuRow(R.drawable.omni_archive,
                stringResource(if (shown.archived) R.string.omni_restore_conversation else R.string.omni_archive_conversation), !busy,
                run { actions.setArchived(shown, !shown.archived) })
            MenuRow(R.drawable.omni_trash_2, stringResource(R.string.omni_dr_delete), !busy, run { onDelete(shown) },
                destructive = true)
        }
    }
}

@Composable
private fun MenuRow(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit, destructive: Boolean = false) {
    val palette = LocalOmniPalette.current
    val color = when {
        !enabled -> palette.tertiaryText
        destructive -> MiuixTheme.colorScheme.error
        else -> palette.text
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 50.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OmniIcon(icon, size = 19.dp, tint = color)
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, color = color)
    }
}

@Composable
private fun RenameDialog(conversation: ConversationSummary?, onDismiss: () -> Unit, onSave: (ConversationSummary, String) -> Unit) {
    val shown = rememberLastConversation(conversation) ?: return
    var title by rememberSaveable(shown.key) { mutableStateOf(shown.title) }
    val focus = remember { FocusRequester() }
    OverlayDialog(show = conversation != null, title = stringResource(R.string.omni_dr_rename_title), onDismissRequest = onDismiss) {
        TextField(title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(shown, title); onDismiss() }))
        Spacer(Modifier.height(16.dp))
        OmniDialogActions(onDismiss, stringResource(R.string.omni_save), { onSave(shown, title); onDismiss() },
            confirmEnabled = title.isNotBlank())
    }
    LaunchedEffect(conversation != null) { if (conversation != null) runCatching { focus.requestFocus() } }
}

/** Keeps the last conversation so a sheet or dialog does not empty while it animates out. */
@Composable
private fun rememberLastConversation(value: ConversationSummary?): ConversationSummary? {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    return last
}

@Composable
private fun DrawerSectionHeading(row: DrawerRow.Heading, onToggle: () -> Unit) {
    val palette = LocalOmniPalette.current
    val label = when (row.kind) {
        DrawerHeadingKind.Scheduled -> stringResource(R.string.omni_scheduled_section)
        DrawerHeadingKind.Pinned -> stringResource(R.string.omni_pinned)
        DrawerHeadingKind.ChatOnly -> stringResource(R.string.omni_chat_only)
        DrawerHeadingKind.Date -> {
            val date = requireNotNull(row.date)
            val locale = LocalConfiguration.current.locales[0]
            when (ChronoUnit.DAYS.between(date, LocalDate.now())) {
                0L -> stringResource(R.string.omni_today)
                1L -> stringResource(R.string.omni_yesterday)
                in 2L..6L -> date.format(DateTimeFormatter.ofPattern("EEE", locale))
                else -> "${date.monthValue}-${date.dayOfMonth}"
            }
        }
    }
    val expansionState = stringResource(if (row.expanded) R.string.omni_collapse else R.string.omni_expand)
    Row(
        Modifier.fillMaxWidth().semantics { stateDescription = expansionState }
            .combinedClickable(role = Role.Button, onClick = onToggle)
            .heightIn(min = 28.dp).padding(start = (4 + row.depth * 20).dp, top = 5.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmniIcon(if (row.kind == DrawerHeadingKind.Scheduled) R.drawable.omni_calendar_clock else R.drawable.omni_history,
            size = 14.dp, tint = palette.tertiaryText)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.tertiaryText)
        Spacer(Modifier.width(8.dp))
        Text(row.count.toString(), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = palette.tertiaryText.copy(alpha = .82f))
    }
}

@Composable
private fun ConversationRow(
    row: DrawerRow.Thread,
    state: NativeHomeState,
    actions: NativeHomeActions,
    background: Color,
    showArchived: Boolean,
    onMenu: () -> Unit,
) {
    val palette = LocalOmniPalette.current
    val conversation = row.conversation
    val busy = conversation.id in state.busyConversationIds
    val archiveLabel = stringResource(if (conversation.archived) R.string.omni_restore_conversation else R.string.omni_archive_conversation)
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = state.leftHanded,
        enableDismissFromEndToStart = !state.leftHanded,
        gesturesEnabled = !busy,
        onDismiss = { direction ->
            if (direction != SwipeToDismissBoxValue.Settled) {
                actions.setArchived(conversation, !conversation.archived)
                // Room/domain state decides removal. Reset the visual if persistence fails.
                scope.launch { swipe.reset() }
            }
        },
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(palette.secondarySurface).padding(horizontal = 12.dp),
                contentAlignment = if (state.leftHanded) Alignment.CenterStart else Alignment.CenterEnd) {
                Text(archiveLabel, color = palette.accent, fontSize = 12.sp)
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().background(background)
            .semantics { customActions = listOf(CustomAccessibilityAction(archiveLabel) {
                if (!busy) actions.setArchived(conversation, !conversation.archived)
                !busy
            }) }
            .combinedClickable(enabled = !busy, role = Role.Button,
                onClick = { actions.open(LegacyDestination.Conversation(conversation.id, conversation.mode, conversation.agentId)) },
                onLongClick = onMenu)
            .padding(start = (4 + row.depth * 20).dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.title, Modifier.weight(1f, fill = false), fontSize = 14.sp, lineHeight = 21.sp,
                        color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Search also finds archived conversations; say so, since opening one restores nothing.
                    if (showArchived && conversation.archived) {
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.omni_dr_archived_badge), fontSize = 10.sp, color = palette.secondaryText,
                            modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(palette.secondarySurface)
                                .padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                row.matchPreview?.let { preview ->
                    Text(preview, fontSize = 12.sp, lineHeight = 17.sp, color = palette.secondaryText, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                if (row.taskCount > 0 || row.childCount > 0) {
                    Text(stringResource(R.string.omni_scheduled_counts, row.taskCount, row.childCount), fontSize = 10.sp, color = palette.tertiaryText)
                }
            }
            if (row.expansionKey != null) {
                OmniIconButton(R.drawable.omni_circle_chevron_down,
                    stringResource(if (row.expanded) R.string.omni_collapse else R.string.omni_expand),
                    { actions.setSectionExpanded(row.expansionKey, !row.expanded) }, size = 18.dp)
            }
        }
    }
}
