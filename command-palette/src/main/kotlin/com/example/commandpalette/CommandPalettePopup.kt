package com.example.commandpalette

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CommandPalettePopup(private val originalEvent: AnActionEvent) {

    private lateinit var popup: JBPopup
    private lateinit var list: JBList<PaletteEntry>
    private lateinit var model: CommandPaletteListModel
    private lateinit var searchField: SearchTextField

    fun show() {
        val allActions = loadAllActions()
        val recentIds = RecentCommandsService.getInstance().getRecentActionIds()
        model = CommandPaletteListModel(allActions, recentIds)

        list = JBList(model).apply {
            cellRenderer = CommandPaletteCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
        }
        selectFirstAction()

        searchField = SearchTextField(false).apply {
            textEditor.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = onQueryChanged()
                override fun removeUpdate(e: DocumentEvent) = onQueryChanged()
                override fun changedUpdate(e: DocumentEvent) = onQueryChanged()
            })
        }

        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        e.consume()
                        moveSelection(1)
                    }
                    KeyEvent.VK_UP -> {
                        e.consume()
                        moveSelection(-1)
                    }
                    KeyEvent.VK_ENTER -> {
                        e.consume()
                        executeSelected()
                    }
                    KeyEvent.VK_ESCAPE -> {
                        e.consume()
                        popup.cancel()
                    }
                }
            }
        })

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    val index = list.locationToIndex(e.point)
                    if (index >= 0) {
                        val entry = model.getElementAt(index)
                        if (entry is PaletteEntry.ActionEntry) {
                            list.selectedIndex = index
                            executeSelected()
                        }
                    }
                }
            }
        })

        val scrollPane = JBScrollPane(list).apply {
            preferredSize = Dimension(500, 350)
            border = JBUI.Borders.empty()
        }

        val panel = JPanel(BorderLayout()).apply {
            add(searchField, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            preferredSize = Dimension(500, 400)
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, searchField.textEditor)
            .setRequestFocus(true)
            .setFocusable(true)
            .setMovable(true)
            .setTitle("Command Palette")
            .createPopup()

        popup.showCenteredInCurrentWindow(originalEvent.project!!)
    }

    private fun onQueryChanged() {
        model.updateFilter(searchField.text)
        selectFirstAction()
    }

    private fun selectFirstAction() {
        val entries = model.getEntries()
        val firstActionIndex = entries.indexOfFirst { it is PaletteEntry.ActionEntry }
        if (firstActionIndex >= 0) {
            list.selectedIndex = firstActionIndex
            list.ensureIndexIsVisible(firstActionIndex)
        } else {
            list.clearSelection()
        }
    }

    private fun moveSelection(delta: Int) {
        val entries = model.getEntries()
        if (entries.isEmpty()) return

        val current = list.selectedIndex
        var next = current + delta

        // Skip headers
        while (next in entries.indices && entries[next] is PaletteEntry.SectionHeader) {
            next += delta
        }

        if (next in entries.indices) {
            list.selectedIndex = next
            list.ensureIndexIsVisible(next)
        }
    }

    private fun executeSelected() {
        val selected = list.selectedValue
        if (selected !is PaletteEntry.ActionEntry) return

        val actionItem = selected.item
        popup.cancel()

        RecentCommandsService.getInstance().recordUsage(actionItem.actionId)

        ApplicationManager.getApplication().invokeLater {
            val action = ActionManager.getInstance().getAction(actionItem.actionId) ?: return@invokeLater
            val focusedComponent = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            val dataContext = DataManager.getInstance().getDataContext(focusedComponent)
            ActionUtil.invokeAction(action, dataContext, ActionPlaces.ACTION_SEARCH, null, null)
        }
    }

    private fun loadAllActions(): List<ActionItem> {
        val actionManager = ActionManager.getInstance()
        val keymap = KeymapManager.getInstance().activeKeymap

        return actionManager.getActionIdList("").asSequence()
            .mapNotNull { id ->
                val action = actionManager.getAction(id) ?: return@mapNotNull null
                if (action is ActionGroup) return@mapNotNull null

                val presentation = action.templatePresentation
                val name = presentation.text
                if (name.isNullOrBlank()) return@mapNotNull null

                val shortcuts = keymap.getShortcuts(id)
                val shortcutText = shortcuts.asSequence()
                    .filterIsInstance<KeyboardShortcut>()
                    .firstOrNull()
                    ?.let { shortcut -> keystrokeToText(shortcut.firstKeyStroke) }
                    ?: ""

                ActionItem(
                    actionId = id,
                    name = name,
                    icon = presentation.icon,
                    shortcutText = shortcutText
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    private fun keystrokeToText(keyStroke: KeyStroke): String {
        val parts = mutableListOf<String>()
        val modifiers = keyStroke.modifiers
        if (modifiers and java.awt.event.InputEvent.META_DOWN_MASK != 0) parts.add("\u2318")
        if (modifiers and java.awt.event.InputEvent.CTRL_DOWN_MASK != 0) parts.add("\u2303")
        if (modifiers and java.awt.event.InputEvent.ALT_DOWN_MASK != 0) parts.add("\u2325")
        if (modifiers and java.awt.event.InputEvent.SHIFT_DOWN_MASK != 0) parts.add("\u21E7")
        parts.add(KeyEvent.getKeyText(keyStroke.keyCode))
        return parts.joinToString("")
    }
}
