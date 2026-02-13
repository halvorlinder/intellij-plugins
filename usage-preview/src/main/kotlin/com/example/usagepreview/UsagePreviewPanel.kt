package com.example.usagepreview

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
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

        // Scroll to line
        val safeLine = line.coerceIn(0, editor.document.lineCount - 1)
        editor.scrollingModel.scrollTo(LogicalPosition(safeLine, 0), ScrollType.CENTER)

        // Highlight the usage line
        val lineStart = editor.document.getLineStartOffset(safeLine)
        val lineEnd = editor.document.getLineEndOffset(safeLine)
        val scheme = EditorColorsManager.getInstance().globalScheme
        val caretRowColor = scheme.getColor(EditorColors.CARET_ROW_COLOR)
        val attributes = TextAttributes().apply {
            backgroundColor = caretRowColor
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
        private val lineTextLabel = JLabel()

        init {
            panel.border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            val topRow = JPanel(BorderLayout(4, 0))
            topRow.isOpaque = false
            topRow.add(iconLabel, BorderLayout.WEST)
            topRow.add(textLabel, BorderLayout.CENTER)

            val wrapper = JPanel()
            wrapper.layout = BoxLayout(wrapper, BoxLayout.Y_AXIS)
            wrapper.isOpaque = false
            topRow.alignmentX = Component.LEFT_ALIGNMENT
            lineTextLabel.alignmentX = Component.LEFT_ALIGNMENT
            wrapper.add(topRow)
            wrapper.add(lineTextLabel)

            panel.add(wrapper, BorderLayout.CENTER)
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

            textLabel.text = value.presentableText
            textLabel.font = list.font

            val trimmedLine = value.lineText.let { if (it.length > 80) it.take(80) + "..." else it }
            lineTextLabel.text = trimmedLine
            lineTextLabel.font = list.font.deriveFont(list.font.size2D - 1f)

            if (isSelected) {
                panel.background = list.selectionBackground
                textLabel.foreground = list.selectionForeground
                lineTextLabel.foreground = list.selectionForeground
            } else {
                panel.background = list.background
                textLabel.foreground = list.foreground
                lineTextLabel.foreground = java.awt.Color.GRAY
            }
            panel.isOpaque = true

            return panel
        }
    }
}
