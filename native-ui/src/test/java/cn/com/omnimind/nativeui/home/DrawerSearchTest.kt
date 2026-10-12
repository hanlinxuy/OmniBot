package cn.com.omnimind.nativeui.home

import cn.com.omnimind.nativeui.ConversationSummary
import cn.com.omnimind.nativeui.DrawerSearchHit
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerSearchTest {
    @Test fun everyTokenMustAppearInAnyOrder() {
        val tokens = DrawerSearch.tokens("  Weekly   REPORT ")
        assertEquals(listOf("weekly", "report"), tokens)
        assertTrue(DrawerSearch.matches("Draft the report\nfor the weekly sync", tokens))
        // The single-substring search before this matched neither half on its own.
        assertFalse(DrawerSearch.matches("weekly sync", tokens))
    }

    @Test fun previewPrefersAFullMatchAndNeverTheTitle() {
        val tokens = DrawerSearch.tokens("report weekly")
        assertEquals("the weekly report is ready",
            DrawerSearch.preview(listOf("Weekly report", "a report", "the weekly  report is ready"), "Weekly report", tokens))
        assertEquals("a report", DrawerSearch.preview(listOf("Weekly report", "a report"), "Weekly report", tokens))
        assertNull(DrawerSearch.preview(listOf("Weekly report"), "Weekly report", tokens))
    }

    @Test fun fragmentsReadEveryStringInAMessageOnce() {
        val content = mapOf("text" to "Hello", "cardData" to mapOf("summary" to "Ran ls", "items" to listOf("hello", "Done", 3)))
        assertEquals(listOf("Hello", "Ran ls", "Done"), DrawerSearch.fragments(content))
    }

    @Test fun messageHitsDecideTheResultsOnceTheyArrive() {
        val a = ConversationSummary(1, "Groceries", "milk", "agent", 2, false)
        val b = ConversationSummary(2, "Trip", "flights", "agent", 1, false)
        fun rows(hits: List<DrawerSearchHit>?) = projectDrawerRows(listOf(a, b), emptyList(), emptyMap(), "passport",
            archivedOnly = false, zone = ZoneId.of("UTC"), messageHits = hits).filterIsInstance<DrawerRow.Thread>()
        assertTrue(rows(null).isEmpty())
        val found = rows(listOf(DrawerSearchHit(b.key, "renew the passport")))
        assertEquals(listOf(2L), found.map { it.conversation.id })
        assertEquals("renew the passport", found.single().matchPreview)
    }

    @Test fun clipboardTextLabelsEachSpeakerAndSkipsEmptyMessages() {
        val text = conversationClipboardText(
            listOf(CopiedMessage(true, " Hi "), CopiedMessage(false, ""), CopiedMessage(false, "Hello!")), "User", "Assistant")
        assertEquals("User：\nHi\n\nAssistant：\nHello!", text)
    }
}
