package cn.com.omnimind.nativeui

import androidx.compose.runtime.Immutable
import cn.com.omnimind.nativeui.omniflow.OmniFlowTab
import java.net.URI
import java.net.URLDecoder

/** Presentation data only. Conversation/ACP lifecycle remains owned by the host runtime. */
@Immutable
data class NativeHomeState(
    val conversations: List<ConversationSummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val theme: ThemePreference = ThemePreference.System,
    val localServiceEnabled: Boolean = false,
    val localServiceBusy: Boolean = false,
    val workspaceMemoryConfigured: Boolean = false,
    val greetingEnabled: Boolean = true,
    val quickPrompts: List<QuickPrompt> = emptyList(),
    val scheduledTasks: List<ScheduledConversationTask> = emptyList(),
    val expandedSections: Map<String, Boolean> = emptyMap(),
    val busyConversationIds: Set<Long> = emptySet(),
    val localService: LocalServiceDetails = LocalServiceDetails(),
    val webActions: List<WebQuickAction> = emptyList(),
    val busyWebAction: String? = null,
    val pendingDestination: LegacyDestination? = null,
    val recentConversationsOnly: Boolean = false,
    val leftHanded: Boolean = false,
    /** Message-text matches for the drawer query; null until that search finishes. */
    val searchHits: DrawerSearchResults? = null,
    /** A one-shot drawer message (deleted, copied, failed). */
    val drawerNotice: DrawerNotice? = null,
)

/** A conversation found by text, with the matching fragment to show under its title. */
@Immutable
data class DrawerSearchHit(val key: String, val preview: String?)

@Immutable
data class DrawerSearchResults(val query: String, val hits: List<DrawerSearchHit>)

enum class DrawerNotice { Deleted, DeleteFailed, Copied, CopyFailed, RenameFailed, PinFailed }

@Immutable
data class ConversationSummary(
    val id: Long,
    val title: String,
    val preview: String,
    val mode: String,
    val updatedAt: Long,
    val pinned: Boolean,
    val parentId: Long? = null,
    val parentMode: String? = null,
    val scheduledTaskId: String? = null,
    val agentId: String? = null,
    val createdAt: Long = updatedAt,
    val archived: Boolean = false,
) {
    val key: String get() = "$mode:$id"
}

@Immutable
data class ScheduledConversationTask(val id: String, val parentId: Long, val parentMode: String?)

/** Ephemeral service details; never save the token in navigation/saved-instance state. */
@Immutable
data class LocalServiceDetails(val endpoint: String = "", val token: String = "") {
    override fun toString(): String = "LocalServiceDetails(endpoint=$endpoint, token=<redacted>)"
}

enum class WebProcessStatus { Unknown, Stopped, Starting, Running }

@Immutable
data class WebQuickAction(
    val pluginId: String,
    val actionId: String,
    val label: String,
    val agentId: String,
    val status: WebProcessStatus = WebProcessStatus.Unknown,
    val canStop: Boolean = false,
    val description: String = "",
) {
    val key: String get() = "$pluginId/$actionId"
    val active: Boolean get() = status == WebProcessStatus.Starting || status == WebProcessStatus.Running
}

/** Actions stay with their existing host services; UI state contains no runtime owner. */
data class NativeHomeActions(
    val open: (LegacyDestination) -> Unit,
    val consumeDestination: () -> Unit,
    val setLocalServiceEnabled: (Boolean) -> Unit,
    val refreshLocalServiceToken: () -> Unit,
    val setArchived: (ConversationSummary, Boolean) -> Unit,
    val setPinned: (ConversationSummary, Boolean) -> Unit = { _, _ -> },
    val rename: (ConversationSummary, String) -> Unit = { _, _ -> },
    val delete: (ConversationSummary) -> Unit = {},
    val copy: (ConversationSummary) -> Unit = {},
    /** Searches message text too; titles and previews filter at once without it. */
    val search: (String) -> Unit = {},
    val consumeDrawerNotice: () -> Unit = {},
    val setSectionExpanded: (String, Boolean) -> Unit,
    val invokeWebAction: (WebQuickAction, Boolean) -> Unit,
    val refresh: () -> Unit,
    /** Opens the read-only native transcript preview (batch 5c); set by the home navigation. */
    val previewTranscript: (ConversationSummary) -> Unit = {},
    /**
     * Resolves an untargeted chat entry (startup preference, then the last
     * visible conversation) and returns the conversation to open, or null for
     * a new one (5e-4). Suspends: it reads the database.
     */
    val resolveStartupChat: suspend () -> ConversationSummary? = { null },
)

@Immutable
data class QuickPrompt(val id: String, val title: String, val prompt: String)

enum class ThemePreference { System, Light, Dark }

/** Typed hand-off to pages whose existing implementation still owns their behavior. */
sealed interface LegacyDestination {
    /** [draft] only fills the Flutter composer (`nativeDraft`); it is never sent by itself. */
    data class Conversation(val id: Long, val mode: String, val agentId: String? = null, val draft: String = "") : LegacyDestination
    data class NewConversation(val draft: String = "") : LegacyDestination

    /**
     * A draft another app shared in (`SharedOpenDraftStore`). The native page
     * fills its composer with the text and attachments; nothing is sent.
     */
    data class SharedDraft(val requestKey: String) : LegacyDestination
    data class TerminalPackage(val packageId: String) : LegacyDestination

    /** Plugin-declared in-app route; validated to stay an app-internal path. */
    data class PluginRoute(val route: String) : LegacyDestination

    /**
     * A conversation named from outside Home (a task-completion notification,
     * 5e-7f). Native Home opens it on the native page when it can, else hands
     * it to Flutter; it waits for the conversation list to load to decide.
     */
    data class OpenConversation(val id: Long, val mode: String) : LegacyDestination

    /** The native workspace browser (5e-8a); null opens the workspace root. */
    data class Workspace(val path: String? = null) : LegacyDestination

    /** The native file preview (5e-8a); [edit] starts in the editor. */
    data class WorkspaceFile(val path: String, val edit: Boolean = false) : LegacyDestination

    /** The native OmniFlow execution center (5n); [functionId] opens that Function's sheet. */
    data class OmniFlow(val tab: OmniFlowTab = OmniFlowTab.Functions, val functionId: String? = null) : LegacyDestination

    /** One native RunLog page (5n). */
    data class RunLog(val runId: String) : LegacyDestination

    /** The native scheduled tasks page; [alarms] opens its exact-alarm tab. */
    data class ScheduledTasks(val alarms: Boolean = false) : LegacyDestination

    enum class Page : LegacyDestination {
        Account, ModelProviders,
        Chat, QuickStart, UserGuide,
    }
}

/**
 * Destinations [NativeHomeApp] pushes as native pages. The host hands only the
 * others to Flutter; before 5e-8a it handed everything but ModelProviders, so
 * a terminal focus or a shared draft opened both pages.
 */
val LegacyDestination.opensNativePage: Boolean
    get() = this == LegacyDestination.Page.ModelProviders ||
        this == LegacyDestination.Page.Account ||
        this is LegacyDestination.TerminalPackage ||
        this is LegacyDestination.SharedDraft ||
        this is LegacyDestination.OpenConversation ||
        this is LegacyDestination.Workspace ||
        this is LegacyDestination.WorkspaceFile ||
        this is LegacyDestination.OmniFlow ||
        this is LegacyDestination.RunLog ||
        this is LegacyDestination.ScheduledTasks

/**
 * The native page an in-app route names (tool-card and plugin actions use
 * the Flutter paths), or null when Flutter still owns it. Before 5n these
 * always opened Flutter, even for pages that were already native.
 */
fun nativeDestinationForRoute(route: String): LegacyDestination? {
    val uri = runCatching { URI(route.trim()) }.getOrNull() ?: return null
    val path = uri.path?.trimEnd('/').orEmpty()
    val query = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
        val parts = pair.split('=', limit = 2)
        if (parts[0].isEmpty()) null
        else parts[0] to URLDecoder.decode(parts.getOrElse(1) { "" }, Charsets.UTF_8).trim()
    }.toMap()
    return when {
        path == "/task/omniflow" -> LegacyDestination.OmniFlow(
            tab = omniFlowTab(query["tab"]),
            functionId = query["functionId"]?.ifEmpty { null },
        )
        path == "/task/run_logs" -> LegacyDestination.OmniFlow(OmniFlowTab.RunLogs)
        path.startsWith("/task/run_log/") -> path.removePrefix("/task/run_log/")
            .takeIf { it.isNotEmpty() && '/' !in it }
            ?.let { LegacyDestination.RunLog(URLDecoder.decode(it, Charsets.UTF_8)) }
        path == "/task/scheduled_tasks" -> LegacyDestination.ScheduledTasks(alarms = query["tab"] == "alarm")
        else -> null
    }
}

/** Dart `_initialTabIndex`: the spellings tool cards and plugins use for the run log tab. */
private fun omniFlowTab(value: String?): OmniFlowTab = when (value?.lowercase()) {
    "run_log", "run_logs", "runlog", "runlogs", "logs", "1" -> OmniFlowTab.RunLogs
    else -> OmniFlowTab.Functions
}

/** Notification and stored modes spell the same mode differently (`normal` vs `agent`). */
fun conversationModeKey(mode: String?): String = when (mode?.trim()?.lowercase()) {
    null, "", "normal", "codex", "acp", "coding", "agent" -> "agent"
    "chat", "chatonly", "chat-only", "chat_only" -> "chat_only"
    else -> mode.trim().lowercase()
}
