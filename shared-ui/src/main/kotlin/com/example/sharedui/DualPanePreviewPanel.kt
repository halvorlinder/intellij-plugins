package com.example.sharedui

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.*
import javax.swing.event.ListSelectionListener

class DualPanePreviewPanel<T : PreviewItem>(
    private val project: Project,
    items: List<T>,
    cellRenderer: ListCellRenderer<T>,
    private val highlightColorProvider: ((T) -> Color)? = null
) : JPanel(BorderLayout()) {

    val list: JBList<T>
    val listModel: DefaultListModel<T> = DefaultListModel()
    private val previewContainer: JPanel
    private var currentEditor: EditorEx? = null
    private var currentFile: VirtualFile? = null
    private val statusLabel = JLabel()
    private val leftPanel: JPanel

    init {
        preferredSize = Dimension(900, 500)

        // Left pane: usage list + optional status
        leftPanel = JPanel(BorderLayout())

        items.forEach { listModel.addElement(it) }
        list = JBList(listModel)
        list.cellRenderer = cellRenderer
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val listScroll = JBScrollPane(list)
        listScroll.minimumSize = Dimension(280, 0)
        listScroll.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER

        leftPanel.add(listScroll, BorderLayout.CENTER)

        statusLabel.border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        statusLabel.isVisible = false
        leftPanel.add(statusLabel, BorderLayout.SOUTH)

        // Right pane: editor preview container
        previewContainer = JPanel(BorderLayout())
        previewContainer.minimumSize = Dimension(400, 0)

        // Split pane
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, previewContainer)
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
        if (items.isNotEmpty()) {
            list.selectedIndex = 0
        }
    }

    fun updateItems(newItems: List<T>) {
        val previousFile = list.selectedValue?.virtualFile
        val previousLine = list.selectedValue?.line ?: 0

        listModel.clear()
        newItems.forEach { listModel.addElement(it) }

        if (newItems.isNotEmpty()) {
            // Try to select nearest item to previous selection
            val nearest = newItems.withIndex().minByOrNull { (_, item) ->
                if (item.virtualFile == previousFile) Math.abs(item.line - previousLine) else Int.MAX_VALUE
            }
            list.selectedIndex = nearest?.index ?: 0
        }
    }

    fun setStatusText(text: String?) {
        if (text != null) {
            statusLabel.text = text
            statusLabel.isVisible = true
        } else {
            statusLabel.isVisible = false
        }
    }

    private fun updatePreview(item: T) {
        if (currentFile == item.virtualFile && currentEditor != null) {
            scrollAndHighlight(currentEditor!!, item)
            return
        }

        releaseCurrentEditor()

        val document = FileDocumentManager.getInstance().getDocument(item.virtualFile) ?: return

        val editor = EditorFactory.getInstance().createViewer(document, project) as EditorEx

        editor.highlighter = EditorHighlighterFactory.getInstance()
            .createEditorHighlighter(project, item.virtualFile)

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
        currentFile = item.virtualFile

        previewContainer.removeAll()
        previewContainer.add(editor.component, BorderLayout.CENTER)
        previewContainer.revalidate()
        previewContainer.repaint()

        SwingUtilities.invokeLater {
            scrollAndHighlight(editor, item)
        }
    }

    private fun scrollAndHighlight(editor: EditorEx, item: T) {
        if (editor.isDisposed) return
        editor.markupModel.removeAllHighlighters()

        val safeLine = item.line.coerceIn(0, editor.document.lineCount - 1)
        editor.scrollingModel.disableAnimation()
        editor.scrollingModel.scrollTo(LogicalPosition(safeLine, 0), ScrollType.CENTER)
        editor.scrollingModel.enableAnimation()

        val lineStart = editor.document.getLineStartOffset(safeLine)
        val lineEnd = editor.document.getLineEndOffset(safeLine)

        val highlightColor = if (highlightColorProvider != null) {
            highlightColorProvider.invoke(item)
        } else {
            defaultHighlightColor()
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

    private fun defaultHighlightColor(): Color {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val baseColor = scheme.getColor(EditorColors.CARET_ROW_COLOR)
            ?: EditorColorsManager.getInstance().globalScheme.defaultBackground
        return if (baseColor.red + baseColor.green + baseColor.blue > 382) {
            Color(
                (baseColor.red * 0.85).toInt().coerceIn(0, 255),
                (baseColor.green * 0.88).toInt().coerceIn(0, 255),
                (baseColor.blue * 0.80).toInt().coerceIn(0, 255)
            )
        } else {
            Color(
                (baseColor.red + (255 - baseColor.red) * 0.15).toInt().coerceIn(0, 255),
                (baseColor.green + (255 - baseColor.green) * 0.12).toInt().coerceIn(0, 255),
                (baseColor.blue + (255 - baseColor.blue) * 0.20).toInt().coerceIn(0, 255)
            )
        }
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
}
