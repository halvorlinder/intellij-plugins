package com.example.vscodewindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx

class CloseAllEditorsInOtherGroupsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fem = FileEditorManagerEx.getInstanceEx(project)
        val current = fem.currentWindow ?: return
        for (window in fem.windows) {
            if (window != current) {
                for (file in window.files) {
                    window.closeFile(file)
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val fem = FileEditorManagerEx.getInstanceEx(project)
        e.presentation.isEnabledAndVisible = fem.windows.size > 1
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
}
