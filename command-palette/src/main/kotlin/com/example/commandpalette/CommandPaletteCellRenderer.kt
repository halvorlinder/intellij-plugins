package com.example.commandpalette

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.*

class CommandPaletteCellRenderer : ListCellRenderer<PaletteEntry> {

    override fun getListCellRendererComponent(
        list: JList<out PaletteEntry>,
        value: PaletteEntry,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        return when (value) {
            is PaletteEntry.SectionHeader -> createHeaderPanel(value)
            is PaletteEntry.ActionEntry -> createActionPanel(value, list, isSelected)
        }
    }

    private fun createHeaderPanel(header: PaletteEntry.SectionHeader): JPanel {
        val label = JLabel(header.title).apply {
            font = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD, UIUtil.getFontSize(UIUtil.FontSize.SMALL))
            foreground = JBColor.gray
            border = JBUI.Borders.empty(4, 8, 2, 8)
        }
        return JPanel(BorderLayout()).apply {
            add(label, BorderLayout.WEST)
            isOpaque = false
        }
    }

    private fun createActionPanel(
        entry: PaletteEntry.ActionEntry,
        list: JList<out PaletteEntry>,
        isSelected: Boolean
    ): JPanel {
        val item = entry.item

        val nameLabel = JLabel(item.name).apply {
            foreground = if (isSelected) list.selectionForeground else list.foreground
        }

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            if (item.icon != null) {
                add(JLabel(item.icon))
            }
            add(nameLabel)
        }

        val shortcutLabel = JLabel(item.shortcutText).apply {
            foreground = JBColor.gray
            font = UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL))
        }

        return JPanel(BorderLayout()).apply {
            add(leftPanel, BorderLayout.WEST)
            add(shortcutLabel, BorderLayout.EAST)
            border = JBUI.Borders.empty(2, 8)
            if (isSelected) {
                background = list.selectionBackground
                isOpaque = true
            } else {
                isOpaque = false
            }
        }
    }
}
