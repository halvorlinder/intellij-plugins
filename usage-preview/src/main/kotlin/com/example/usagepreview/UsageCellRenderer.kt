package com.example.usagepreview

import com.intellij.openapi.fileTypes.FileTypeManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

class UsageCellRenderer : ListCellRenderer<UsageItem> {
    private val panel = JPanel(BorderLayout(4, 0))
    private val iconLabel = JLabel()
    private val textLabel = JLabel()

    init {
        panel.border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
        panel.add(iconLabel, BorderLayout.WEST)
        panel.add(textLabel, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out UsageItem>,
        value: UsageItem,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(value.virtualFile.name)
        iconLabel.icon = fileType.icon

        val trimmedLine = value.lineText.let { if (it.length > 60) it.take(60) + "..." else it }
        textLabel.text = "${value.presentableText}  $trimmedLine"
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
