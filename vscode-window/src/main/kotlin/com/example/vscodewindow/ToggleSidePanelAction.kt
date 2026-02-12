package com.example.vscodewindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager

class ToggleSidePanelAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = PanelStateService.getInstance(project)
        val toolWindows = PanelStateService.getToolWindowsAtAnchor(project, ToolWindowAnchor.LEFT)

        val anyVisible = toolWindows.any { it.isVisible }
        if (anyVisible) {
            toolWindows.filter { it.isVisible }.forEach { it.hide() }
        } else {
            val lastId = service.getLastShown(ToolWindowAnchor.LEFT) ?: "Project"
            val twm = ToolWindowManager.getInstance(project)
            val target = twm.getToolWindow(lastId) ?: toolWindows.firstOrNull()
            target?.activate(null)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
}
