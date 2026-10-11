package cn.com.omnimind.bot.ui.chat.browser

import cn.com.omnimind.nativeui.chat.browser.BrowserDialog
import cn.com.omnimind.nativeui.chat.browser.BrowserDownload
import cn.com.omnimind.nativeui.chat.browser.BrowserExternalOpen
import cn.com.omnimind.nativeui.chat.browser.BrowserLink
import cn.com.omnimind.nativeui.chat.browser.BrowserPermission
import cn.com.omnimind.nativeui.chat.browser.BrowserSession
import cn.com.omnimind.nativeui.chat.browser.BrowserTab
import cn.com.omnimind.nativeui.chat.browser.browserRiskFor

/** Reads `BrowserUseEngine.liveSessionSnapshot` (5m); null when no tab is open. */
internal object BrowserSessionMapper {
    fun map(snapshot: Map<String, Any?>): BrowserSession? {
        if (snapshot["available"] != true) return null
        return BrowserSession(
            workspaceId = snapshot.string("workspaceId"),
            url = snapshot.string("currentUrl"),
            title = snapshot.string("title"),
            loading = snapshot["isLoading"] == true,
            canGoBack = snapshot["canGoBack"] == true,
            canGoForward = snapshot["canGoForward"] == true,
            sslError = snapshot["hasSslError"] == true,
            desktopMode = snapshot["isDesktopMode"] == true,
            bookmarked = snapshot["isBookmarked"] == true,
            risk = browserRiskFor(snapshot["riskChallengeDetected"] == true, snapshot["recommendedNextAction"] as? String),
            tabs = snapshot.maps("tabs").mapNotNull { tab ->
                val id = (tab["tabId"] as? Number)?.toInt() ?: return@mapNotNull null
                BrowserTab(id, tab.string("url"), tab.string("title"), tab["isActive"] == true, tab["isLoading"] == true)
            },
            history = snapshot.maps("history").map { BrowserLink(it.string("url"), it.string("title")) },
            bookmarks = snapshot.maps("bookmarks").map { BrowserLink(it.string("url"), it.string("title")) },
            downloads = snapshot.maps("downloads").map { item ->
                BrowserDownload(
                    id = item.string("id"),
                    fileName = item.string("fileName"),
                    status = item.string("status"),
                    progress = (item["progress"] as? Number)?.toFloat()?.coerceIn(0f, 1f),
                    error = item.string("errorMessage").ifBlank { null },
                    canPause = item["canPause"] == true,
                    canResume = item["canResume"] == true,
                    canCancel = item["canCancel"] == true,
                    canRetry = item["canRetry"] == true,
                    canOpen = item["canOpenFile"] == true,
                )
            },
            dialog = (snapshot["pendingDialog"] as? Map<*, *>)?.let { dialog ->
                BrowserDialog(dialog.string("requestId"), dialog.string("type"), dialog.string("message"), dialog.string("defaultValue"))
            },
            permission = (snapshot["permissionPrompt"] as? Map<*, *>)?.let { prompt ->
                BrowserPermission(prompt.string("requestId"), prompt.string("origin"),
                    (prompt["resources"] as? List<*>).orEmpty().map { it.toString() })
            },
            externalOpen = (snapshot["externalOpenPrompt"] as? Map<*, *>)?.let { prompt ->
                BrowserExternalOpen(prompt.string("requestId"), prompt.string("target"))
            },
        )
    }

    private fun Map<*, *>.string(key: String): String = this[key]?.toString().orEmpty()

    private fun Map<*, *>.maps(key: String): List<Map<*, *>> = (this[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
}
