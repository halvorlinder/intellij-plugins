package com.example.vscodewindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import java.awt.KeyboardFocusManager
import javax.swing.JTree
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class OpenFileWithoutFocusAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (file.isDirectory) {
            val tree = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? JTree ?: return
            val row = tree.leadSelectionRow
            if (row < 0) return
            if (tree.isExpanded(row)) tree.collapseRow(row) else tree.expandRow(row)
        } else {
            FileEditorManager.getInstance(project).openFile(file, false)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}

class ProjectViewSpaceRegistrar(private val project: Project) : ToolWindowManagerListener {
    private var registered = false

    override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow.id == "Project" && !registered) {
            registered = true
            val action = OpenFileWithoutFocusAction()
            action.registerCustomShortcutSet(
                CustomShortcutSet(
                    KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), null),
                    KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.ALT_DOWN_MASK), null)
                ),
                toolWindow.component
            )
        }
    }
}
