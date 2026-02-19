package com.example.commandpalette

import javax.swing.Icon

data class ActionItem(
    val actionId: String,
    val name: String,
    val icon: Icon?,
    val shortcutText: String,
    val groupPath: String,
    val nameLower: String = name.lowercase(),
    val words: List<String> = nameLower.split(WORD_SPLIT_REGEX).filter { it.isNotEmpty() }
) {
    companion object {
        private val WORD_SPLIT_REGEX = Regex("[\\s/\\-_.]+")
    }
}
