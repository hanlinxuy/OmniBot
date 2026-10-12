package cn.com.omnimind.bot.ui.nativehome

import cn.com.omnimind.nativeui.ConversationSummary
import cn.com.omnimind.nativeui.DrawerSearchHit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerMessageSearchTest {
    private val trip = ConversationSummary(1, "Trip", "flights", "agent", updatedAt = 10, pinned = false)
    private val food = ConversationSummary(2, "Groceries", "milk", "agent", updatedAt = 20, pinned = false)

    @Test fun findsMessageTextAndReadsEachTranscriptOnce() = runBlocking {
        val loads = mutableListOf<Long>()
        val search = DrawerMessageSearch { conversation ->
            loads += conversation.id
            if (conversation.id == 1L) listOf(mapOf("content" to mapOf("text" to "Renew the passport first"))) else emptyList()
        }
        assertEquals(listOf(DrawerSearchHit(trip.key, "Renew the passport first")), search.search("passport", listOf(trip, food)))
        // Typing on reuses the index; a title match needs no transcript at all.
        search.search("passport renew", listOf(trip, food))
        assertEquals(listOf(DrawerSearchHit(food.key, null)), search.search("groceries", listOf(trip, food)))
        assertEquals(listOf(1L, 2L), loads)
    }

    @Test fun aChangedConversationIsReadAgain() = runBlocking {
        var loads = 0
        val search = DrawerMessageSearch { loads++; emptyList() }
        search.search("x", listOf(trip))
        search.search("x", listOf(trip.copy(updatedAt = 11)))
        assertEquals(2, loads)
    }
}
