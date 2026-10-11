package cn.com.omnimind.bot.agent

import android.content.Context
import android.view.ViewGroup

interface AgentBrowserLiveSessionHandle {
    val workspaceId: String

    fun closeSession()
}

class LiveAgentBrowserSessionStore<T : AgentBrowserLiveSessionHandle> {
    private val sessions = linkedMapOf<String, T>()
    @Volatile
    private var currentWorkspaceId: String? = null

    @Synchronized
    fun acquire(workspaceId: String, create: () -> T): T {
        val existing = sessions[workspaceId]
        currentWorkspaceId = workspaceId
        return existing ?: create().also { sessions[workspaceId] = it }
    }

    fun current(): T? = currentWorkspaceId?.let(sessions::get)

    @Synchronized
    fun find(workspaceId: String): T? = sessions[workspaceId]

    @Synchronized
    fun clear() {
        sessions.values.toList().forEach { it.closeSession() }
        sessions.clear()
        currentWorkspaceId = null
    }
}

object LiveAgentBrowserSessionManager {
    private val store = LiveAgentBrowserSessionStore<BrowserUseEngine>()

    fun acquireEngine(
        context: Context,
        workspaceManager: AgentWorkspaceManager,
        agentRunId: String,
        workspace: AgentWorkspaceDescriptor
    ): BrowserUseEngine {
        val engine = store.acquire(workspace.id) {
            BrowserUseEngine(
                context = context,
                workspaceManager = workspaceManager,
                agentRunId = agentRunId,
                workspace = workspace
            )
        }
        return engine
    }

    fun currentSnapshot(): Map<String, Any?> {
        return store.current()?.liveSessionSnapshot() ?: BrowserUseEngine.unavailableSnapshot()
    }

    suspend fun handleCurrentCall(
        method: String,
        arguments: Map<String, Any?> = emptyMap()
    ): Any? {
        val engine = store.current()
        return if (engine == null) {
            if (
                method == "getSnapshot" ||
                method == "getLiveBrowserSessionSnapshot" ||
                method == "capturePreviewFrame"
            ) {
                BrowserUseEngine.unavailableSnapshot()
            } else {
                throw IllegalStateException("当前没有可用的浏览器会话")
            }
        } else {
            engine.handleHostCall(method = method, arguments = arguments)
        }
    }

    suspend fun executeCurrent(request: BrowserUseRequest): BrowserUseOutcome? {
        return store.current()?.execute(request)
    }

    suspend fun captureCurrentFramePng(): ByteArray? {
        return store.current()?.captureActiveFramePng()
    }

    fun attachActiveTabTo(
        container: ViewGroup,
        hostContext: Context,
        workspaceId: String
    ): Boolean {
        val engine = store.current()
        if (engine == null || engine.workspaceId != workspaceId) {
            return false
        }
        return engine.attachActiveTabTo(container = container, hostContext = hostContext)
    }

    fun detachActiveTabFrom(container: ViewGroup? = null) {
        store.current()?.detachActiveTabFrom(container)
    }

    // ---- Native observers (5m) ----------------------------------------------

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(String, Map<String, Any?>) -> Unit>()

    /** Every snapshot an engine publishes, with its workspace; called on the main thread. */
    fun addSnapshotListener(listener: (workspaceId: String, snapshot: Map<String, Any?>) -> Unit) { listeners += listener }
    fun removeSnapshotListener(listener: (String, Map<String, Any?>) -> Unit) { listeners -= listener }

    internal fun publish(workspaceId: String, snapshot: Map<String, Any?>) {
        listeners.forEach { runCatching { it(workspaceId, snapshot) } }
    }

    /** The snapshot of one conversation's browser, never another's. */
    fun snapshotFor(workspaceId: String): Map<String, Any?> =
        store.find(workspaceId)?.liveSessionSnapshot() ?: BrowserUseEngine.unavailableSnapshot(workspaceId = workspaceId)

    /**
     * Runs a host command on [workspaceId]'s own engine. The Flutter overlay
     * sent every command to whichever engine ran last, so a tap could drive
     * another conversation's browser (fixed in 5m for the native overlay).
     */
    suspend fun handleCall(workspaceId: String, method: String, arguments: Map<String, Any?> = emptyMap()): Any? {
        val engine = store.find(workspaceId) ?: throw IllegalStateException("No browser session for this conversation")
        return engine.handleHostCall(method = method, arguments = arguments)
    }

    /** Shows [workspaceId]'s active tab in [container]; false when that conversation has no browser. */
    fun attachTabOf(workspaceId: String, container: ViewGroup, hostContext: Context): Boolean =
        store.find(workspaceId)?.attachActiveTabTo(container = container, hostContext = hostContext) ?: false

    fun detachTabOf(workspaceId: String, container: ViewGroup) {
        store.find(workspaceId)?.detachActiveTabFrom(container)
    }

    fun releaseRunOwnership() {
        // 浏览器会话改为全局保活；run 结束时不销毁会话。
    }
}
