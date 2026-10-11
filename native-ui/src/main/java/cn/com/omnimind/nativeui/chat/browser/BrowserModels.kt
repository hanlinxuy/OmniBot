package cn.com.omnimind.nativeui.chat.browser

import androidx.compose.runtime.Immutable

/*
 * The agent browser as the native chat page sees it (batch 5m), typed from
 * `BrowserUseEngine.liveSessionSnapshot`. The engine owns every tab and the
 * WebView; this is a read-only picture of it plus pure display rules, ported
 * from `chat/widgets/chat_browser_overlay.dart`.
 */

@Immutable
data class BrowserTab(val id: Int, val url: String, val title: String, val active: Boolean, val loading: Boolean)

@Immutable
data class BrowserLink(val url: String, val title: String)

@Immutable
data class BrowserDownload(
    val id: String,
    val fileName: String,
    val status: String,
    /** 0..1, or null while the size is unknown. */
    val progress: Float?,
    val error: String?,
    val canPause: Boolean,
    val canResume: Boolean,
    val canCancel: Boolean,
    val canRetry: Boolean,
    val canOpen: Boolean,
)

/** A JS `alert`/`confirm`/`prompt` waiting for the user. */
@Immutable
data class BrowserDialog(val requestId: String, val type: String, val message: String, val defaultValue: String) {
    val isAlert: Boolean get() = type == "alert"
    val isPrompt: Boolean get() = type == "prompt"
}

@Immutable
data class BrowserPermission(val requestId: String, val origin: String, val resources: List<String>)

@Immutable
data class BrowserExternalOpen(val requestId: String, val target: String)

/** Why a page stopped automation (Dart `_riskBannerText` keys). */
enum class BrowserRisk { RateLimited, AccessDenied, Challenge }

@Immutable
data class BrowserSession(
    val workspaceId: String,
    val url: String,
    val title: String,
    val loading: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val sslError: Boolean,
    val desktopMode: Boolean,
    val bookmarked: Boolean,
    val risk: BrowserRisk?,
    val tabs: List<BrowserTab>,
    val history: List<BrowserLink>,
    val bookmarks: List<BrowserLink>,
    val downloads: List<BrowserDownload>,
    val dialog: BrowserDialog?,
    val permission: BrowserPermission?,
    val externalOpen: BrowserExternalOpen?,
)

/** Dart `_riskBannerText`: the engine's recommendation picks the message. */
fun browserRiskFor(detected: Boolean, recommendedNextAction: String?): BrowserRisk? = when {
    !detected -> null
    recommendedNextAction == "wait_before_retrying_and_reduce_request_rate" -> BrowserRisk.RateLimited
    recommendedNextAction == "stop_automatic_retry_and_use_manual_access" -> BrowserRisk.AccessDenied
    else -> BrowserRisk.Challenge
}

/** The address pill: the host in bold, then the rest (Dart `_displayUrlParts`). */
@Immutable
data class AddressParts(val host: String, val rest: String)

fun addressParts(url: String): AddressParts {
    val match = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]*)(.*)$").find(url.trim()) ?: return AddressParts(url.trim(), "")
    val host = match.groupValues[1].removePrefix("www.")
    val rest = match.groupValues[2].let { if (it == "/") "" else it }
    return AddressParts(host, rest)
}

/** Pages the address bar never treats as a site. Dart showed `about:blank` as `about://`. */
fun isBlankPage(url: String): Boolean = url.isBlank() || url.trim().startsWith("about:")

private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
private val HOST_LIKE = Regex("^[^\\s/]+\\.[^\\s/]{2,}(/.*)?$|^localhost(:\\d+)?(/.*)?$|^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")

/**
 * What the address bar opens for typed [input]. Dart prefixed `https://` to
 * anything, so `best pizza` became `https://best pizza`; words now search
 * with [searchTemplate] (`%s` is the query) and only host-like text is a URL.
 */
fun addressTarget(input: String, searchTemplate: String = "https://www.bing.com/search?q=%s"): String? {
    val text = input.trim()
    if (text.isEmpty()) return null
    // Before the scheme check: `localhost:3000` reads as scheme `localhost`.
    if (HOST_LIKE.matches(text)) return "https://$text"
    if (SCHEME.containsMatchIn(text) && !text.contains(' ')) {
        return if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("omnibot:") || text.startsWith("about:")) text
            else null
    }
    return searchTemplate.replace("%s", java.net.URLEncoder.encode(text, Charsets.UTF_8))
}

/** Overlay geometry (Dart `chat_page_browser.dart` constants), in dp. */
object BrowserWindow {
    const val DEFAULT_WIDTH = 360f
    const val DEFAULT_HEIGHT = 420f
    const val MIN_WIDTH = 280f
    const val MIN_HEIGHT = 240f
    const val MAX_WIDTH_FRACTION = .92f
    const val MAX_HEIGHT_FRACTION = .78f
    const val MARGIN = 16f
    const val TOP_MARGIN = 64f
}

@Immutable
data class BrowserWindowFrame(val x: Float, val y: Float, val width: Float, val height: Float)

/** Keeps the window on screen and within its size limits (Dart `_clampBrowserOverlay`). */
fun BrowserWindowFrame.clamped(containerWidth: Float, containerHeight: Float): BrowserWindowFrame {
    val maxW = (containerWidth * BrowserWindow.MAX_WIDTH_FRACTION).coerceAtLeast(BrowserWindow.MIN_WIDTH)
    val maxH = (containerHeight * BrowserWindow.MAX_HEIGHT_FRACTION).coerceAtLeast(BrowserWindow.MIN_HEIGHT)
    val w = width.coerceIn(BrowserWindow.MIN_WIDTH, maxW)
    val h = height.coerceIn(BrowserWindow.MIN_HEIGHT, maxH)
    val maxX = (containerWidth - w - BrowserWindow.MARGIN).coerceAtLeast(BrowserWindow.MARGIN)
    val maxY = (containerHeight - h - BrowserWindow.MARGIN).coerceAtLeast(BrowserWindow.TOP_MARGIN)
    return BrowserWindowFrame(x.coerceIn(BrowserWindow.MARGIN, maxX), y.coerceIn(BrowserWindow.TOP_MARGIN, maxY), w, h)
}

/** First placement: top right, below the bar (Dart `_initializeBrowserOverlay`). */
fun defaultBrowserFrame(containerWidth: Float, containerHeight: Float): BrowserWindowFrame =
    BrowserWindowFrame(containerWidth - BrowserWindow.DEFAULT_WIDTH - BrowserWindow.MARGIN, BrowserWindow.TOP_MARGIN,
        BrowserWindow.DEFAULT_WIDTH, BrowserWindow.DEFAULT_HEIGHT).clamped(containerWidth, containerHeight)

/** The commands the overlay sends; the app runs them on the conversation's own engine. */
data class BrowserActions(
    val navigate: (String) -> Unit = {},
    val reload: () -> Unit = {},
    val stop: () -> Unit = {},
    val back: () -> Unit = {},
    val forward: () -> Unit = {},
    val newTab: () -> Unit = {},
    val selectTab: (Int) -> Unit = {},
    val closeTab: (Int) -> Unit = {},
    val toggleBookmark: () -> Unit = {},
    val removeBookmark: (String) -> Unit = {},
    val clearHistory: () -> Unit = {},
    val toggleDesktopMode: () -> Unit = {},
    val resolveDialog: (requestId: String, accept: Boolean, value: String?) -> Unit = { _, _, _ -> },
    val answerPermission: (requestId: String, grant: Boolean) -> Unit = { _, _ -> },
    val answerExternalOpen: (requestId: String, open: Boolean) -> Unit = { _, _ -> },
    val download: (taskId: String, action: DownloadAction) -> Unit = { _, _ -> },
)

enum class DownloadAction { Pause, Resume, Cancel, Retry, Open, OpenFolder, Delete }
