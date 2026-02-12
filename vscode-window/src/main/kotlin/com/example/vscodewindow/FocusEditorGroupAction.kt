package com.example.vscodewindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx

abstract class FocusEditorGroupAction(private val groupIndex: Int) : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fem = FileEditorManagerEx.getInstanceEx(project)
        val windows = fem.windows
        if (groupIndex < windows.size) {
            val target = windows[groupIndex]
            fem.currentWindow = target
            target.requestFocus(true)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        val fem = FileEditorManagerEx.getInstanceEx(project)
        e.presentation.isEnabled = groupIndex < fem.windows.size
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
}

class FocusEditorGroup1Action : FocusEditorGroupAction(0)
class FocusEditorGroup2Action : FocusEditorGroupAction(1)
class FocusEditorGroup3Action : FocusEditorGroupAction(2)
