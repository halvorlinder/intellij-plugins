package com.example.commandpalette

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

class CommandPaletteCellRenderer : ListCellRenderer<PaletteEntry> {

    private val iconWidth = JBUI.scale(20)

    override fun getListCellRendererComponent(
        list: JList<out PaletteEntry>,
        value: PaletteEntry,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        return when (value) {
            is PaletteEntry.SectionHeader -> createHeaderPanel(value, list)
            is PaletteEntry.ActionEntry -> createActionPanel(value, list, isSelected)
        }
    }

    private fun createHeaderPanel(header: PaletteEntry.SectionHeader, list: JList<out PaletteEntry>): JPanel {
        val label = JLabel(header.title).apply {
            font = UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL))
            foreground = UIUtil.getLabelDisabledForeground()
            border = JBUI.Borders.empty(6, 8, 2, 8)
        }
        return JPanel(BorderLayout()).apply {
            add(label, BorderLayout.WEST)
            background = list.background
            isOpaque = true
        }
    }

    private fun createActionPanel(
        entry: PaletteEntry.ActionEntry,
        list: JList<out PaletteEntry>,
        isSelected: Boolean
    ): JPanel {
        val item = entry.item
        val fg = if (isSelected) list.selectionForeground else list.foreground
        val dimFg = if (isSelected) list.selectionForeground else UIUtil.getLabelDisabledForeground()

        // Icon gutter - fixed width so text aligns
        val iconLabel = JLabel().apply {
            preferredSize = Dimension(iconWidth, JBUI.scale(16))
            horizontalAlignment = SwingConstants.CENTER
            if (item.icon != null) icon = item.icon
        }

        // Action name
        val nameLabel = JLabel(item.name).apply {
            foreground = fg
            font = UIUtil.getLabelFont()
        }

        // Shortcut inline after name
        val shortcutLabel = if (item.shortcutText.isNotEmpty()) {
            JLabel(" ${item.shortcutText}").apply {
                foreground = dimFg
                font = UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL))
            }
        } else null

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(iconLabel)
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(nameLabel)
            if (shortcutLabel != null) add(shortcutLabel)
        }

        // Group path right-aligned
        val rightLabel = if (item.groupPath.isNotEmpty()) {
            JLabel(item.groupPath).apply {
                foreground = dimFg
                font = UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL))
                border = JBUI.Borders.emptyRight(4)
            }
        } else null

        return JPanel(BorderLayout()).apply {
            add(leftPanel, BorderLayout.WEST)
            if (rightLabel != null) add(rightLabel, BorderLayout.EAST)
            border = JBUI.Borders.empty(3, 8, 3, 8)
            if (isSelected) {
                background = list.selectionBackground
            } else {
                background = list.background
            }
            isOpaque = true
        }
    }
}
