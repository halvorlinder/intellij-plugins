package com.example.gitfilecheckout

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import java.nio.charset.StandardCharsets

class GitFileCheckoutAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(GitFileCheckoutAction::class.java)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (project == null || file == null || file.isDirectory) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val repoManager = GitRepositoryManager.getInstance(project)
        val repo = repoManager.getRepositoryForFile(file)
        e.presentation.isEnabledAndVisible = repo != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val repoManager = GitRepositoryManager.getInstance(project)
        val repo = repoManager.getRepositoryForFile(file) ?: run {
            Messages.showErrorDialog(project, "File is not in a Git repository.", "Git File Checkout")
            return
        }

        BranchOrCommitChooser.show(project, repo) { ref ->
            checkoutFileFromRef(project, file, repo, ref)
        }
    }

    private fun checkoutFileFromRef(
        project: Project,
        file: VirtualFile,
        repo: git4idea.repo.GitRepository,
        ref: String
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Checking out file from $ref...", false) {
            override fun run(indicator: ProgressIndicator) {
                val repoRoot = repo.root
                val relativePath = com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(file, repoRoot)
                if (relativePath == null) {
                    showError(project, "Could not determine relative path for file.")
                    return
                }

                val handler = GitLineHandler(project, repoRoot, GitCommand.SHOW)
                handler.addParameters("$ref:$relativePath")
                handler.setStdoutSuppressed(true)

                val result = Git.getInstance().runCommand(handler)

                if (!result.success()) {
                    val errorOutput = result.errorOutputAsJoinedString
                    val message = when {
                        errorOutput.contains("does not exist") || errorOutput.contains("exists on disk, but not in") ->
                            "File '$relativePath' does not exist at ref '$ref'."
                        errorOutput.contains("unknown revision") || errorOutput.contains("bad revision") ->
                            "Unknown revision or ref: '$ref'."
                        else -> "Failed to get file from '$ref':\n$errorOutput"
                    }
                    showError(project, message)
                    return
                }

                val content = result.outputAsJoinedString

                ApplicationManager.getApplication().invokeLater {
                    WriteCommandAction.runWriteCommandAction(project, "Checkout File from $ref", null, {
                        file.setBinaryContent(content.toByteArray(StandardCharsets.UTF_8))
                    })
                    file.refresh(false, false)
                }
            }
        })
    }

    private fun showError(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, message, "Git File Checkout")
        }
    }
}
