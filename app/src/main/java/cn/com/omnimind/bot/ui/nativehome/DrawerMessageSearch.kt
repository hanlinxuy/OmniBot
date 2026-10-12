package cn.com.omnimind.bot.ui.nativehome

import cn.com.omnimind.nativeui.ConversationSummary
import cn.com.omnimind.nativeui.DrawerSearchHit
import cn.com.omnimind.nativeui.home.DrawerSearch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Full-text drawer search (Dart `_performConversationSearch`): titles and
 * previews first, then each conversation's messages. A conversation's text
 * is read once and kept until it changes, so typing more of a query does
 * not reload every transcript.
 */
internal class DrawerMessageSearch(private val messages: suspend (ConversationSummary) -> List<Map<String, Any?>>) {
    private class Index(val signature: String, val fragments: List<String>)

    private val indexes = HashMap<String, Index>()

    /** Hits in [conversations] order; cancels cleanly when a newer query starts. */
    suspend fun search(query: String, conversations: List<ConversationSummary>): List<DrawerSearchHit> {
        val tokens = DrawerSearch.tokens(query)
        if (tokens.isEmpty()) return emptyList()
        indexes.keys.retainAll(conversations.map { it.key }.toSet())
        return conversations.mapNotNull { conversation ->
            coroutineContext.ensureActive()
            val metadata = listOf(conversation.title, conversation.preview)
            val fragments = if (DrawerSearch.matches(metadata.joinToString("\n"), tokens)) metadata
            else index(conversation).fragments.takeIf { DrawerSearch.matches(it.joinToString("\n"), tokens) }
            fragments?.let { DrawerSearchHit(conversation.key, DrawerSearch.preview(it, conversation.title, tokens)) }
        }
    }

    private suspend fun index(conversation: ConversationSummary): Index {
        val signature = "${conversation.updatedAt}|${conversation.title}|${conversation.preview}"
        indexes[conversation.key]?.takeIf { it.signature == signature }?.let { return it }
        val fragments = listOf(conversation.title, conversation.preview) +
            runCatching { messages(conversation) }.getOrDefault(emptyList()).flatMap { DrawerSearch.fragments(it["content"]) }
        return Index(signature, fragments).also { indexes[conversation.key] = it }
    }
}
