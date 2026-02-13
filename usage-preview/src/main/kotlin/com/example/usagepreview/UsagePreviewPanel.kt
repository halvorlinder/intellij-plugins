package com.example.usagepreview

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import javax.swing.event.ListSelectionListener

class UsagePreviewPanel(
    private val project: Project,
    usages: List<UsageItem>,
    private val onNavigate: (UsageItem) -> Unit
) : JPanel(BorderLayout()) {

    val list: JBList<UsageItem>
    private val previewContainer: JPanel
    private var currentEditor: EditorEx? = null
    private var currentFile: com.intellij.openapi.vfs.VirtualFile? = null

    init {
        preferredSize = Dimension(900, 500)

        // Left pane: usage list
        val listModel = DefaultListModel<UsageItem>()
        usages.forEach { listModel.addElement(it) }
        list = JBList(listModel)
        list.cellRenderer = UsageCellRenderer()
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val listScroll = JBScrollPane(list)
        listScroll.minimumSize = Dimension(280, 0)
        listScroll.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER

        // Right pane: editor preview container
        previewContainer = JPanel(BorderLayout())
        previewContainer.minimumSize = Dimension(400, 0)

        // Split pane
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, previewContainer)
        splitPane.resizeWeight = 0.35
        splitPane.dividerSize = 3
        add(splitPane, BorderLayout.CENTER)

        // Selection listener
        list.addListSelectionListener(ListSelectionListener { e ->
            if (e.valueIsAdjusting) return@ListSelectionListener
            val selected = list.selectedValue ?: return@ListSelectionListener
            updatePreview(selected)
        })

        // Select first item
        if (usages.isNotEmpty()) {
            list.selectedIndex = 0
        }
    }

    private fun updatePreview(usage: UsageItem) {
        if (currentFile == usage.virtualFile && currentEditor != null) {
            // Same file — just scroll and re-highlight
            scrollAndHighlight(currentEditor!!, usage.line)
            return
        }

        // Different file — create new editor
        releaseCurrentEditor()

        val document = FileDocumentManager.getInstance().getDocument(usage.virtualFile) ?: return

        val editor = EditorFactory.getInstance().createViewer(document, project) as EditorEx

        // Set syntax highlighting based on file type
        editor.highlighter = com.intellij.openapi.editor.highlighter.EditorHighlighterFactory.getInstance()
            .createEditorHighlighter(project, usage.virtualFile)

        // Configure the editor
        val settings = editor.settings
        settings.isLineNumbersShown = true
        settings.isFoldingOutlineShown = false
        settings.isLineMarkerAreaShown = false
        settings.isIndentGuidesShown = true
        settings.additionalLinesCount = 0
        settings.isCaretRowShown = false
        settings.isRightMarginShown = false
        settings.isUseSoftWraps = false

        editor.colorsScheme = EditorColorsManager.getInstance().globalScheme

        currentEditor = editor
        currentFile = usage.virtualFile

        previewContainer.removeAll()
        previewContainer.add(editor.component, BorderLayout.CENTER)
        previewContainer.revalidate()
        previewContainer.repaint()

        // Scroll after layout
        SwingUtilities.invokeLater {
            scrollAndHighlight(editor, usage.line)
        }
    }

    private fun scrollAndHighlight(editor: EditorEx, line: Int) {
        // Remove old highlights
        editor.markupModel.removeAllHighlighters()

        // Scroll to line (no animation)
        val safeLine = line.coerceIn(0, editor.document.lineCount - 1)
        editor.scrollingModel.disableAnimation()
        editor.scrollingModel.scrollTo(LogicalPosition(safeLine, 0), com.intellij.openapi.editor.ScrollType.CENTER)
        editor.scrollingModel.enableAnimation()

        // Highlight the usage line with a visible color
        val lineStart = editor.document.getLineStartOffset(safeLine)
        val lineEnd = editor.document.getLineEndOffset(safeLine)
        val scheme = EditorColorsManager.getInstance().globalScheme
        val baseColor = scheme.getColor(EditorColors.CARET_ROW_COLOR) ?: editor.colorsScheme.defaultBackground
        // Make it noticeably brighter/darker than caret row
        val highlightColor = if (baseColor.red + baseColor.green + baseColor.blue > 382) {
            // Light theme — darken
            java.awt.Color(
                (baseColor.red * 0.85).toInt().coerceIn(0, 255),
                (baseColor.green * 0.88).toInt().coerceIn(0, 255),
                (baseColor.blue * 0.80).toInt().coerceIn(0, 255)
            )
        } else {
            // Dark theme — lighten
            java.awt.Color(
                (baseColor.red + (255 - baseColor.red) * 0.15).toInt().coerceIn(0, 255),
                (baseColor.green + (255 - baseColor.green) * 0.12).toInt().coerceIn(0, 255),
                (baseColor.blue + (255 - baseColor.blue) * 0.20).toInt().coerceIn(0, 255)
            )
        }
        val attributes = TextAttributes().apply {
            backgroundColor = highlightColor
        }

        editor.markupModel.addRangeHighlighter(
            lineStart,
            lineEnd,
            HighlighterLayer.SELECTION - 1,
            attributes,
            HighlighterTargetArea.LINES_IN_RANGE
        )
    }

    private fun releaseCurrentEditor() {
        currentEditor?.let { editor ->
            EditorFactory.getInstance().releaseEditor(editor)
        }
        currentEditor = null
        currentFile = null
    }

    fun dispose() {
        releaseCurrentEditor()
    }

    private class UsageCellRenderer : ListCellRenderer<UsageItem> {
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
            // Ensure text gets clipped with ellipsis rather than expanding the cell
            textLabel.minimumSize = Dimension(0, textLabel.preferredSize.height)

            if (isSelected) {
                panel.background = list.selectionBackground
                textLabel.foreground = list.selectionForeground
            } else {
                panel.background = list.background
                textLabel.foreground = list.foreground
            }
            panel.isOpaque = true

            // Constrain panel width so it doesn't expand beyond the list viewport
            val listWidth = list.visibleRect.width
            if (listWidth > 0) {
                panel.preferredSize = Dimension(listWidth, panel.preferredSize.height)
            }

            return panel
        }
    }
}
