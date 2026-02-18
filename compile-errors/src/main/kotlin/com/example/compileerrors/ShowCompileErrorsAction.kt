package com.example.compileerrors

import com.example.sharedui.DualPanePreviewPanel
import com.example.sharedui.PreviewPopupBuilder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import java.awt.Color

class ShowCompileErrorsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.getService(CompileErrorsCacheService::class.java)

        val panel = DualPanePreviewPanel(
            project,
            service.errors,
            CompileErrorsCellRenderer()
        ) { item ->
            when (item.category) {
                CompilerMessageCategory.ERROR -> Color(80, 0, 0, 40)
                CompilerMessageCategory.WARNING -> Color(80, 70, 0, 40)
                else -> Color(0, 0, 80, 40)
            }
        }

        fun updateStatus() {
            val errors = service.errors
            val errorCount = errors.count { it.category == CompilerMessageCategory.ERROR }
            val warningCount = errors.count { it.category == CompilerMessageCategory.WARNING }
            val suffix = if (service.isCompiling) " (compiling...)" else ""
            panel.setStatusText("$errorCount errors, $warningCount warnings$suffix")
        }

        updateStatus()

        val refreshListener: () -> Unit = {
            ApplicationManager.getApplication().invokeLater {
                panel.updateItems(service.errors)
                updateStatus()
            }
        }

        service.addRefreshListener(refreshListener)

        PreviewPopupBuilder.show(
            project,
            panel,
            onNavigate = { item ->
                OpenFileDescriptor(project, item.virtualFile, item.line, item.column).navigate(true)
            },
            onCancel = {
                service.removeRefreshListener(refreshListener)
            }
        )

        service.triggerCompile()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
