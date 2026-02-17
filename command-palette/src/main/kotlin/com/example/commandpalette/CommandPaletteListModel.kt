package com.example.commandpalette

import javax.swing.AbstractListModel

sealed class PaletteEntry {
    data class SectionHeader(val title: String) : PaletteEntry()
    data class ActionEntry(val item: ActionItem) : PaletteEntry()
}

class CommandPaletteListModel(
    private val allActions: List<ActionItem>,
    private val recentActionIds: List<String>
) : AbstractListModel<PaletteEntry>() {

    private val actionById: Map<String, ActionItem> = allActions.associateBy { it.actionId }
    private var entries: List<PaletteEntry> = buildEntries("")

    override fun getSize(): Int = entries.size

    override fun getElementAt(index: Int): PaletteEntry = entries[index]

    fun updateFilter(query: String) {
        val oldSize = entries.size
        entries = buildEntries(query)
        val newSize = entries.size
        fireContentsChanged(this, 0, maxOf(oldSize, newSize) - 1)
    }

    fun getEntries(): List<PaletteEntry> = entries

    private fun buildEntries(query: String): List<PaletteEntry> {
        val tokens = query.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }

        val recentSet = recentActionIds.toSet()

        // Empty query: only show recents
        if (tokens.isEmpty()) {
            val recentActions = recentActionIds.mapNotNull { actionById[it] }
            if (recentActions.isEmpty()) return emptyList()
            val result = mutableListOf<PaletteEntry>()
            result.add(PaletteEntry.SectionHeader("Recent"))
            recentActions.forEach { result.add(PaletteEntry.ActionEntry(it)) }
            return result
        }

        // Score and filter all actions
        val scored = allActions.mapNotNull { item ->
            val score = scoreMatch(item, tokens) ?: return@mapNotNull null
            item to score
        }

        val recentMatches = mutableListOf<ActionItem>()
        val otherMatches = mutableListOf<Pair<ActionItem, Int>>()

        for ((item, score) in scored) {
            if (item.actionId in recentSet) {
                recentMatches.add(item)
            } else {
                otherMatches.add(item to score)
            }
        }

        // Sort recents by their recency order, others by score descending then name
        recentMatches.sortBy { recentActionIds.indexOf(it.actionId) }
        otherMatches.sortWith(compareByDescending<Pair<ActionItem, Int>> { it.second }.thenBy { it.first.nameLower })

        val result = mutableListOf<PaletteEntry>()

        if (recentMatches.isNotEmpty()) {
            result.add(PaletteEntry.SectionHeader("Recent"))
            recentMatches.forEach { result.add(PaletteEntry.ActionEntry(it)) }
        }

        if (otherMatches.isNotEmpty()) {
            result.add(PaletteEntry.SectionHeader("Actions"))
            // Cap at 200 to keep the list snappy
            otherMatches.take(200).forEach { result.add(PaletteEntry.ActionEntry(it.first)) }
        }

        return result
    }

    /**
     * Scores how well an action matches the query tokens.
     * Returns null if any token doesn't match.
     *
     * Scoring per token:
     *  - Full name starts with token: 100
     *  - Any word starts with token:   50
     *  - Substring match:              10
     *
     * Bonus:
     *  - Has keyboard shortcut:        +5  (popular actions)
     *  - Shorter name:                 +bonus (prefer concise names)
     */
    private fun scoreMatch(item: ActionItem, tokens: List<String>): Int? {
        var totalScore = 0

        for (token in tokens) {
            val tokenScore = when {
                item.nameLower.startsWith(token) -> 100
                item.words.any { it.startsWith(token) } -> 50
                item.nameLower.contains(token) -> 10
                else -> return null
            }
            totalScore += tokenScore
        }

        if (item.shortcutText.isNotEmpty()) totalScore += 5
        // Prefer shorter (more specific) names: bonus up to 20 for short names
        totalScore += maxOf(0, 20 - item.name.length / 3)

        return totalScore
    }
}
