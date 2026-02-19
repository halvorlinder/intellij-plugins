package com.example.sharedui

import com.intellij.icons.AllIcons
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
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.ListSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

data class FolderNodeData(val displayPath: String)
data class FileNodeData(val virtualFile: VirtualFile)

class DualPanePreviewPanel<T : PreviewItem>(
    private val project: Project,
    initialItems: List<T>,
    cellRenderer: ListCellRenderer<T>,
    private val highlightColorProvider: ((T) -> Color?)? = null,
    private val treeItemText: ((T) -> String) = { "L${it.line + 1}" },
    private val itemMarker: ((T) -> Boolean)? = null
) : JPanel(BorderLayout()) {

    val list: JBList<T>
    val listModel: DefaultListModel<T> = DefaultListModel()
    private val tree: Tree
    private var treeModel: DefaultTreeModel
    private val previewContainer: JPanel
    private var currentEditor: EditorEx? = null
    private var currentFile: VirtualFile? = null
    private val statusLabel = JLabel()
    private val leftPanel: JPanel
    private val cardLayout: CardLayout
    private val cardPanel: JPanel
    private var treeMode = false
    private var items: List<T> = initialItems
    private val keyHandlers = mutableListOf<(KeyEvent) -> Boolean>()

    // Track expansion state across tree rebuilds
    private val expandedFolders = mutableSetOf<String>()
    private val expandedFiles = mutableSetOf<String>()
    private var hasUserToggledExpansion = false

    val selectedItem: T?
        get() = if (treeMode) getSelectedTreeItem() else list.selectedValue

    init {
        preferredSize = Dimension(1300, 750)

        // Left pane
        leftPanel = JPanel(BorderLayout())

        // Flat list
        initialItems.forEach { listModel.addElement(it) }
        list = JBList(listModel)
        list.cellRenderer = cellRenderer
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val listScroll = JBScrollPane(list)
        listScroll.minimumSize = Dimension(320, 0)
        listScroll.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER

        // Tree view
        treeModel = buildTreeModel(initialItems)
        tree = Tree(treeModel)
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = createTreeCellRenderer()

        val treeScroll = JBScrollPane(tree)
        treeScroll.minimumSize = Dimension(320, 0)
        treeScroll.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER

        // Card layout to switch between list and tree
        cardLayout = CardLayout()
        cardPanel = JPanel(cardLayout)
        cardPanel.add(listScroll, "list")
        cardPanel.add(treeScroll, "tree")
        cardLayout.show(cardPanel, "list")

        leftPanel.add(cardPanel, BorderLayout.CENTER)

        statusLabel.border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        statusLabel.isVisible = false
        leftPanel.add(statusLabel, BorderLayout.SOUTH)

        // Right pane: editor preview
        previewContainer = JPanel(BorderLayout())
        previewContainer.minimumSize = Dimension(550, 0)

        // Split pane
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, previewContainer)
        splitPane.resizeWeight = 0.30
        splitPane.dividerSize = 3
        add(splitPane, BorderLayout.CENTER)

        // List selection -> preview
        list.addListSelectionListener(ListSelectionListener { e ->
            if (e.valueIsAdjusting) return@ListSelectionListener
            val selected = list.selectedValue ?: return@ListSelectionListener
            updatePreview(selected)
        })

        // Tree selection -> preview
        tree.addTreeSelectionListener {
            val item = getSelectedTreeItem() ?: return@addTreeSelectionListener
            updatePreview(item)
        }

        // Shared key handler dispatch on both list and tree
        val sharedKeyListener = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                for (handler in keyHandlers) {
                    if (handler(e)) {
                        e.consume()
                        return
                    }
                }
            }
        }
        list.addKeyListener(sharedKeyListener)
        tree.addKeyListener(sharedKeyListener)

        // Space key: expand/collapse in tree mode
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_SPACE) {
                    val path = tree.selectionPath ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    if (!node.isLeaf) {
                        if (tree.isExpanded(path)) {
                            tree.collapsePath(path)
                        } else {
                            tree.expandPath(path)
                        }
                        e.consume()
                    }
                }
            }
        })

        // T key: toggle tree mode
        addKeyHandler { e ->
            if (e.keyCode == KeyEvent.VK_T) {
                toggleTreeMode()
                true
            } else false
        }

        // Select first item
        if (initialItems.isNotEmpty()) {
            list.selectedIndex = 0
        }

        // Expand all tree nodes initially
        expandAllTree()
    }

    fun addKeyHandler(handler: (KeyEvent) -> Boolean) {
        keyHandlers.add(handler)
    }

    fun toggleTreeMode() {
        treeMode = !treeMode
        if (treeMode) {
            val currentItem = list.selectedValue
            cardLayout.show(cardPanel, "tree")
            tree.requestFocusInWindow()
            if (currentItem != null) selectItemInTree(currentItem)
        } else {
            val currentItem = getSelectedTreeItem()
            cardLayout.show(cardPanel, "list")
            list.requestFocusInWindow()
            if (currentItem != null) {
                val idx = (0 until listModel.size()).firstOrNull { listModel.getElementAt(it) == currentItem }
                if (idx != null) list.selectedIndex = idx
            }
        }
    }

    fun updateItems(newItems: List<T>) {
        items = newItems
        val previousFile = selectedItem?.virtualFile
        val previousLine = selectedItem?.line ?: 0

        // Update flat list
        listModel.clear()
        newItems.forEach { listModel.addElement(it) }

        // Update tree (preserve expansion state)
        saveExpansionState()
        treeModel = buildTreeModel(newItems)
        tree.model = treeModel
        if (hasUserToggledExpansion) {
            restoreExpansionState()
        } else {
            expandAllTree()
        }

        if (newItems.isNotEmpty()) {
            if (treeMode) {
                val nearest = newItems.minByOrNull { item ->
                    if (item.virtualFile == previousFile) Math.abs(item.line - previousLine) else Int.MAX_VALUE
                }
                if (nearest != null) selectItemInTree(nearest)
            } else {
                val nearest = newItems.withIndex().minByOrNull { (_, item) ->
                    if (item.virtualFile == previousFile) Math.abs(item.line - previousLine) else Int.MAX_VALUE
                }
                list.selectedIndex = nearest?.index ?: 0
            }
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

    private fun buildTreeModel(items: List<T>): DefaultTreeModel {
        val root = DefaultMutableTreeNode("Root")
        val basePath = project.basePath ?: ""

        // Group items by parent directory
        val byDir = items.groupBy { it.virtualFile.parent?.path ?: "" }

        for ((dirPath, dirItems) in byDir.toSortedMap()) {
            val relativePath = dirPath.removePrefix(basePath).removePrefix("/")
            val folderNode = DefaultMutableTreeNode(FolderNodeData(relativePath.ifEmpty { "." }))

            // Group by file within directory, sort alphabetically
            val byFile = dirItems.groupBy { it.virtualFile }
            for ((file, fileItems) in byFile.entries.sortedBy { it.key.name.lowercase() }) {
                val fileNode = DefaultMutableTreeNode(FileNodeData(file))

                // Sort items by line within file
                for (item in fileItems.sortedBy { it.line }) {
                    fileNode.add(DefaultMutableTreeNode(item))
                }
                folderNode.add(fileNode)
            }
            root.add(folderNode)
        }

        return DefaultTreeModel(root)
    }

    private fun createTreeCellRenderer(): ColoredTreeCellRenderer {
        return object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                tree: JTree,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                val node = value as? DefaultMutableTreeNode ?: return
                when (val userObj = node.userObject) {
                    is FolderNodeData -> {
                        icon = AllIcons.Nodes.Folder
                        append(userObj.displayPath, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }
                    is FileNodeData -> {
                        icon = FileTypeManager.getInstance()
                            .getFileTypeByFileName(userObj.virtualFile.name).icon
                        append(userObj.virtualFile.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }
                    is PreviewItem -> {
                        @Suppress("UNCHECKED_CAST")
                        val item = userObj as T
                        val text = treeItemText(item)
                        val isMarked = itemMarker?.invoke(item) == true
                        val attrs = if (isMarked) {
                            SimpleTextAttributes(
                                SimpleTextAttributes.STYLE_PLAIN,
                                JBColor(Color(0, 128, 0), Color(100, 200, 100))
                            )
                        } else {
                            SimpleTextAttributes.REGULAR_ATTRIBUTES
                        }
                        append(text, attrs)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getSelectedTreeItem(): T? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return getItemFromNode(node)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getItemFromNode(node: DefaultMutableTreeNode): T? {
        val userObj = node.userObject
        if (userObj is PreviewItem) return userObj as T
        // For folder/file nodes, return first leaf descendant
        if (node.childCount > 0) {
            return getItemFromNode(node.getChildAt(0) as DefaultMutableTreeNode)
        }
        return null
    }

    private fun selectItemInTree(item: T) {
        val root = treeModel.root as DefaultMutableTreeNode
        val path = findNodePath(root, item)
        if (path != null) {
            tree.selectionPath = path
            tree.scrollPathToVisible(path)
        } else {
            // Fallback: select first leaf
            selectFirstLeaf()
        }
    }

    private fun findNodePath(node: DefaultMutableTreeNode, item: T): TreePath? {
        if (node.userObject === item || node.userObject == item) {
            return TreePath(node.path)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            val path = findNodePath(child, item)
            if (path != null) return path
        }
        return null
    }

    private fun selectFirstLeaf() {
        val root = treeModel.root as DefaultMutableTreeNode
        val firstLeaf = root.firstLeaf
        if (firstLeaf != null) {
            val path = TreePath(firstLeaf.path)
            tree.selectionPath = path
            tree.scrollPathToVisible(path)
        }
    }

    private fun expandAllTree() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    private fun saveExpansionState() {
        expandedFolders.clear()
        expandedFiles.clear()
        val root = treeModel.root as DefaultMutableTreeNode
        for (i in 0 until root.childCount) {
            val folderNode = root.getChildAt(i) as DefaultMutableTreeNode
            val folderData = folderNode.userObject as? FolderNodeData ?: continue
            if (tree.isExpanded(TreePath(folderNode.path))) {
                expandedFolders.add(folderData.displayPath)
            }
            for (j in 0 until folderNode.childCount) {
                val fileNode = folderNode.getChildAt(j) as DefaultMutableTreeNode
                val fileData = fileNode.userObject as? FileNodeData ?: continue
                if (tree.isExpanded(TreePath(fileNode.path))) {
                    expandedFiles.add(fileData.virtualFile.path)
                }
            }
        }
        hasUserToggledExpansion = true
    }

    private fun restoreExpansionState() {
        val root = treeModel.root as DefaultMutableTreeNode
        for (i in 0 until root.childCount) {
            val folderNode = root.getChildAt(i) as DefaultMutableTreeNode
            val folderData = folderNode.userObject as? FolderNodeData ?: continue
            if (folderData.displayPath in expandedFolders) {
                tree.expandPath(TreePath(folderNode.path))
                for (j in 0 until folderNode.childCount) {
                    val fileNode = folderNode.getChildAt(j) as DefaultMutableTreeNode
                    val fileData = fileNode.userObject as? FileNodeData ?: continue
                    if (fileData.virtualFile.path in expandedFiles) {
                        tree.expandPath(TreePath(fileNode.path))
                    }
                }
            }
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
            highlightColorProvider.invoke(item) ?: defaultHighlightColor()
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
