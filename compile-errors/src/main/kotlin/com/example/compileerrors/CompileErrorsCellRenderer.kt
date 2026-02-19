package com.example.compileerrors

import com.intellij.icons.AllIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

class CompileErrorsCellRenderer : ListCellRenderer<CompileErrorItem> {
    private val panel = JPanel(BorderLayout(4, 0))
    private val iconLabel = JLabel()
    private val textLabel = JLabel()

    init {
        panel.border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
        panel.add(iconLabel, BorderLayout.WEST)
        panel.add(textLabel, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out CompileErrorItem>,
        value: CompileErrorItem,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        iconLabel.icon = when (value.severity) {
            ErrorSeverity.ERROR -> AllIcons.General.Error
            ErrorSeverity.WARNING -> AllIcons.General.Warning
        }

        val fileName = value.virtualFile.name
        val lineNum = value.line + 1
        val msg = value.message.let { if (it.length > 80) it.take(80) + "..." else it }
        textLabel.text = "$fileName:$lineNum  $msg"
        textLabel.font = list.font
        textLabel.minimumSize = Dimension(0, textLabel.preferredSize.height)

        if (isSelected) {
            panel.background = list.selectionBackground
            textLabel.foreground = list.selectionForeground
        } else {
            panel.background = list.background
            textLabel.foreground = list.foreground
        }
        panel.isOpaque = true

        val listWidth = list.visibleRect.width
        if (listWidth > 0) {
            panel.preferredSize = Dimension(listWidth, panel.preferredSize.height)
        }

        return panel
    }
}
