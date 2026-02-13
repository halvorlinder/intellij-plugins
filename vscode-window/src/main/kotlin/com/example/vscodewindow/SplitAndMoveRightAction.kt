package com.example.vscodewindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.ui.Splitter
import java.awt.Component
import java.awt.Container

class SplitAndMoveRightAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fem = FileEditorManagerEx.getInstanceEx(project)
        val window = fem.currentWindow ?: return
        val file = window.selectedFile ?: return

        window.split(
            orientation = 1,
            forceSplit = true,
            virtualFile = file,
            focusNew = true
        ) ?: return

        if (window.files.size > 1) {
            window.closeFile(file)
        }
    }
}

class EqualizeSplitWidthsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val fem = FileEditorManagerEx.getInstanceEx(project)
        equalizeSplitters(fem.splitters)
    }

    private fun equalizeSplitters(container: Container) {
        for (child in container.components) {
            if (child is Splitter) {
                val leftCount = countLeaves(child.firstComponent)
                val rightCount = countLeaves(child.secondComponent)
                child.proportion = leftCount.toFloat() / (leftCount + rightCount)
            }
            if (child is Container) {
                equalizeSplitters(child)
            }
        }
    }

    private fun countLeaves(component: Component?): Int {
        if (component == null) return 0
        if (component is Splitter) {
            return countLeaves(component.firstComponent) + countLeaves(component.secondComponent)
        }
        return 1
    }
}
