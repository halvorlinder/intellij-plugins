package com.example.compileerrors

import com.example.sharedui.DualPanePreviewPanel
import com.example.sharedui.PreviewPopupBuilder
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.awt.Color
import java.awt.event.KeyEvent

class ShowCompileErrorsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val service = project.getService(MavenErrorCacheService::class.java)

        val initialErrors = service.errors.filter { it.severity == ErrorSeverity.ERROR }

        val panel = DualPanePreviewPanel(
            project,
            initialErrors,
            CompileErrorsCellRenderer(),
            highlightColorProvider = { item ->
                when (item.severity) {
                    ErrorSeverity.ERROR -> Color(80, 0, 0, 40)
                    ErrorSeverity.WARNING -> Color(80, 70, 0, 40)
                }
            },
            treeItemText = { item ->
                val msg = item.message.let { if (it.length > 60) it.take(60) + "..." else it }
                "L${item.line + 1}: $msg"
            }
        )

        val settings = CompileErrorsSettings.getInstance(project)

        fun autoResolveTag(): String {
            return if (settings.autoResolveEnabled) "auto-resolve: on" else "auto-resolve: off"
        }

        fun updateStatus(errors: List<CompileErrorItem>) {
            val errorCount = errors.size
            if (service.isRunning) {
                panel.setStatusText("compiling...")
            } else if (errorCount == 0) {
                panel.setStatusText("No errors. Press R to compile | ${autoResolveTag()}")
            } else {
                panel.setStatusText("$errorCount error${if (errorCount != 1) "s" else ""} | ${autoResolveTag()}")
            }
        }

        updateStatus(initialErrors)

        val refreshListener: () -> Unit = {
            ApplicationManager.getApplication().invokeLater {
                val errors = service.errors.filter { it.severity == ErrorSeverity.ERROR }
                panel.updateItems(errors)
                updateStatus(errors)
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

        panel.addKeyHandler { keyEvent ->
            if (keyEvent.keyCode == KeyEvent.VK_R) {
                triggerMavenCompile(project, currentFile)
                true
            } else if (keyEvent.keyCode == KeyEvent.VK_A) {
                settings.autoResolveEnabled = !settings.autoResolveEnabled
                val errors = service.errors.filter { it.severity == ErrorSeverity.ERROR }
                updateStatus(errors)
                true
            } else {
                false
            }
        }
    }

    private fun triggerMavenCompile(project: Project, currentFile: VirtualFile?) {
        val workingDir = findMavenWorkingDir(project, currentFile) ?: return

        val configSettings = MavenRunConfigurationType.createRunnerAndConfigurationSettings(
            null,
            null,
            MavenRunnerParameters(true, workingDir, null as String?, listOf("spotless:apply", "test-compile"), emptyList()),
            project
        )

        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        if (executor != null) {
            ExecutionUtil.runConfiguration(configSettings, executor)
        }
    }

    private fun findMavenWorkingDir(project: Project, file: VirtualFile?): String? {
        if (file == null) return null

        val mavenProjectsManager = MavenProjectsManager.getInstance(project)
        var current: VirtualFile? = file
        while (current != null) {
            val pomFile = current.findChild("pom.xml")
            if (pomFile != null) {
                val mavenProject = mavenProjectsManager.findProject(pomFile)
                if (mavenProject != null) {
                    return mavenProject.directory
                }
            }
            current = current.parent
        }
        return null
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
