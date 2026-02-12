package com.example.mavencontextrunner

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.project.MavenProjectsManager

abstract class RunMavenGoalAction(private val goal: String, private val description: String) : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        val mavenProject = findMavenProject(project, file) ?: return
        val workingDir = mavenProject.directory
        
        runMavenGoal(project, workingDir, goal)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        val enabled = project != null && file != null && findMavenProject(project, file) != null
        e.presentation.isEnabledAndVisible = enabled
        
        if (enabled && project != null && file != null) {
            val mavenProject = findMavenProject(project, file)
            e.presentation.text = "$description (${mavenProject?.displayName ?: "unknown"})"
        }
    }

    private fun findMavenProject(project: Project, file: VirtualFile): org.jetbrains.idea.maven.project.MavenProject? {
        val mavenProjectsManager = MavenProjectsManager.getInstance(project)
        
        // Walk up the directory tree to find the containing Maven project
        var current: VirtualFile? = file
        while (current != null) {
            val pomFile = current.findChild("pom.xml")
            if (pomFile != null) {
                val mavenProject = mavenProjectsManager.findProject(pomFile)
                if (mavenProject != null) {
                    return mavenProject
                }
            }
            current = current.parent
        }
        return null
    }

    private fun runMavenGoal(project: Project, workingDir: String, goal: String) {
        val params = MavenRunnerParameters(
            true,           // isPomExecution
            workingDir,     // workingDirPath
            null as String?,           // pomFileName (null = use default pom.xml)
            listOf(goal),   // goals
            emptyList()     // profilesIds
        )

        val configSettings = MavenRunConfigurationType.createRunnerAndConfigurationSettings(
            null,           // settings (null = use defaults)
            null,           // mavenSettings  
            params,         // parameters
            project         // project
        )

        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        if (executor != null) {
            ExecutionUtil.runConfiguration(configSettings, executor)
        }
    }
}

class MavenVerifyAction : RunMavenGoalAction("verify", "Maven Verify")
class MavenTestAction : RunMavenGoalAction("test", "Maven Test")
class SpotlessApplyAction : RunMavenGoalAction("spotless:apply", "Spotless Apply")
