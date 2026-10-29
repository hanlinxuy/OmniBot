package cn.com.omnimind.bot.agent

import java.io.File

internal fun selectShortMemoryIndexes(
    entries: List<WorkspaceShortMemoryEntry>,
    expected: List<WorkspaceShortMemoryEntry>,
): Set<Int> = expected.map { target ->
    val matches = entries.indices.filter { index ->
        val entry = entries[index]
        entry.id == target.id && entry.date == target.date &&
            entry.content == target.content && entry.time == target.time
    }
    require(matches.size == 1) { "Memory changed; reload before deleting" }
    matches.single()
}.toSet()

/** Uses indexes from one parsed snapshot, so a batch cannot shift its own targets. */
internal fun removeShortMemoryBlocks(
    content: String,
    selectedIndexes: Set<Int>,
    isEntry: (String) -> Boolean
): String {
    var entryIndex = -1
    var removing = false
    val result = content.lineSequence().filter { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("- ") || trimmed.startsWith("#")) {
            removing = false
            if (isEntry(line)) {
                entryIndex++
                removing = entryIndex in selectedIndexes
            }
        }
        !removing
    }.joinToString("\n")
    return if (content.endsWith("\n") && result.isNotEmpty() && !result.endsWith("\n")) {
        "$result\n"
    } else {
        result
    }
}

internal fun appendDailyMemoryLine(file: File, line: String) {
    if (file.isFile && file.length() > 0 && !file.readText().endsWith("\n")) {
        file.appendText("\n")
    }
    file.appendText("$line\n")
}
