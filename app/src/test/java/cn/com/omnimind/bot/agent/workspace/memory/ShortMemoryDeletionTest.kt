package cn.com.omnimind.bot.agent

import java.nio.file.Files
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class ShortMemoryDeletionTest {
    private val entry = WorkspaceShortMemoryEntry("a", "2026-09-06", "08:00:00", "text", 0)

    @Test fun `snapshot selection preserves original positions and deduplicates requests`() {
        val second = entry.copy(id = "b")
        assertEquals(setOf(1), selectShortMemoryIndexes(listOf(entry, second), listOf(second, second)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `edited text is rejected before deletion`() {
        selectShortMemoryIndexes(listOf(entry.copy(content = "changed")), listOf(entry))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing target rejects the complete batch`() {
        selectShortMemoryIndexes(listOf(entry), listOf(entry, entry.copy(id = "missing")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ambiguous duplicate identity is rejected`() {
        selectShortMemoryIndexes(listOf(entry, entry), listOf(entry))
    }

    private fun remove(text: String, vararg indexes: Int) =
        removeShortMemoryBlocks(text, indexes.toSet()) { it.trim().startsWith("- [") }

    @Test fun `batch deletes original indexes without shifting targets`() {
        assertEquals("- [10:00] keep\n", remove("- [08:00] a\n- [09:00] b\n- [10:00] keep\n", 0, 1))
    }

    @Test fun `deletes only selected duplicate and its continuation`() {
        assertEquals("- [08:00] same\n# summary\nsummary text\n", remove(
            "- [08:00] same\n- [08:00] same\ncontinuation\n# summary\nsummary text\n", 1))
    }

    @Test fun `preserves metadata and unselected multiline blocks`() {
        assertEquals("# day\n- metadata\nmetadata body\n- [09:00] keep\nkeep body\n", remove(
            "# day\n- [08:00] delete\nprivate continuation\n- metadata\nmetadata body\n- [09:00] keep\nkeep body\n", 0))
    }

    @Test fun `empty selection is unchanged`() {
        val text = "# day\n- [08:00] memory\n"
        assertEquals(text, remove(text))
    }

    @Test fun `append after last deletion remains a separate entry`() {
        val file = Files.createTempFile("daily-memory", ".md").toFile()
        try {
            file.writeText(remove("# day\n- [08:00] delete\n", 0))
            appendDailyMemoryLine(file, "- [09:00] new")
            assertEquals("# day\n- [09:00] new\n", file.readText())
        } finally {
            file.delete()
        }
    }

    @Test fun `append repairs existing missing final newline without altering content`() {
        val file = Files.createTempFile("daily-memory", ".md").toFile()
        try {
            file.writeText("# day")
            appendDailyMemoryLine(file, "- [09:00] new")
            assertEquals("# day\n- [09:00] new\n", file.readText())
        } finally {
            file.delete()
        }
    }

    @Test fun `deleted final entry followed by append is visible to daily parser`() {
        val file = Files.createTempFile("daily-memory-parser", ".md").toFile()
        try {
            file.writeText(remove("# day\n- [08:00:00] old\n", 0))
            appendDailyMemoryLine(file, "- [09:00:00] new")
            val service = Mockito.mock(WorkspaceMemoryService::class.java, Mockito.CALLS_REAL_METHODS)
            val parser = WorkspaceMemoryService::class.java.getDeclaredMethod(
                "parseDailyShortMemoryEntries", LocalDate::class.java, String::class.java,
            ).apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val entries = parser.invoke(service, LocalDate.of(2026, 10, 30), file.readText()) as List<WorkspaceShortMemoryEntry>
            assertEquals(listOf("new"), entries.map { it.content })
            file.writeText("# legacy day")
            appendDailyMemoryLine(file, "- [09:00:00] legacy new")
            @Suppress("UNCHECKED_CAST")
            val legacy = parser.invoke(service, LocalDate.of(2026, 10, 30), file.readText()) as List<WorkspaceShortMemoryEntry>
            assertEquals(listOf("legacy new"), legacy.map { it.content })
        } finally {
            file.delete()
        }
    }

    @Test fun `deleting last entry removes remaining private text`() {
        assertEquals("# day\n", remove("# day\n- [08:00] delete\nprivate continuation\n", 0))
    }
}
