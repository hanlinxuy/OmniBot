package cn.com.omnimind.nativeui.home

/**
 * Drawer search rules (Dart `_HomeDrawerSearch`): every whitespace-separated
 * token must appear, case-insensitively, in the conversation's title,
 * summaries or message text; the preview is the first fragment that matched.
 */
object DrawerSearch {
    fun tokens(query: String): List<String> = normalize(query).split(' ').filter { it.isNotEmpty() }

    fun normalize(text: String): String = text.replace(WHITESPACE, " ").trim().lowercase()

    fun matches(text: String, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        val normalized = normalize(text)
        return tokens.all(normalized::contains)
    }

    /**
     * The fragment to show under the title: one with every token, else one
     * with any token, never the title itself.
     */
    fun preview(fragments: List<String>, title: String, tokens: List<String>): String? {
        val candidates = fragments.map { it.replace(WHITESPACE, " ").trim() }.filter { it.isNotEmpty() && it != title.trim() }
        return candidates.firstOrNull { matches(it, tokens) }
            ?: candidates.firstOrNull { candidate -> normalize(candidate).let { text -> tokens.any(text::contains) } }
    }

    /** Every string inside a message's content, in order, each once (Dart `_collectSearchableText`). */
    fun fragments(content: Any?): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        fun walk(value: Any?) {
            when (value) {
                is String -> if (normalize(value).let { it.isNotEmpty() && seen.add(it) }) out += value
                is Map<*, *> -> value.values.forEach(::walk)
                is Iterable<*> -> value.forEach(::walk)
            }
        }
        walk(content)
        return out
    }

    private val WHITESPACE = Regex("\\s+")
}

/** One text message, for copying a conversation. */
data class CopiedMessage(val fromUser: Boolean, val text: String)

/**
 * A conversation as plain text, oldest first, with who said each part (Dart
 * `buildConversationClipboardText`). [userLabel] and [assistantLabel] follow
 * the app language; Dart always used the Chinese labels.
 */
fun conversationClipboardText(messages: List<CopiedMessage>, userLabel: String, assistantLabel: String): String =
    messages.mapNotNull { message ->
        message.text.trim().takeIf { it.isNotEmpty() }?.let { "${if (message.fromUser) userLabel else assistantLabel}：\n$it" }
    }.joinToString("\n\n")
