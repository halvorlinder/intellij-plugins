package com.example.mavencontextrunner

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.project.MavenProjectsManager

abstract class RunMavenGoalAction(
    private val goal: String,
    private val description: String,
    private val runSpotlessFirst: Boolean = false
) : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val mavenProject = findMavenProject(project, file) ?: return
        val workingDir = mavenProject.directory

        if (runSpotlessFirst) {
            runMavenGoalThen(project, workingDir, "spotless:apply") {
                runMavenGoal(project, workingDir, goal)
            }
        } else {
            runMavenGoal(project, workingDir, goal)
        }
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

    private fun runMavenGoalThen(project: Project, workingDir: String, firstGoal: String, onSuccess: () -> Unit) {
        val configSettings = createMavenConfig(project, workingDir, firstGoal)

        val connection = project.messageBus.connect()
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
                if (env.runnerAndConfigurationSettings === configSettings) {
                    connection.disconnect()
                    if (exitCode == 0) {
                        VirtualFileManager.getInstance().asyncRefresh {
                            ApplicationManager.getApplication().invokeLater {
                                onSuccess()
                            }
                        }
                    }
                }
            }
        })

        launchMavenConfig(configSettings)
    }

    private fun runMavenGoal(project: Project, workingDir: String, goal: String) {
        launchMavenConfig(createMavenConfig(project, workingDir, goal))
    }

    private fun createMavenConfig(project: Project, workingDir: String, goal: String) =
        MavenRunConfigurationType.createRunnerAndConfigurationSettings(
            null,
            null,
            MavenRunnerParameters(true, workingDir, null as String?, listOf(goal), emptyList()),
            project
        )

    private fun launchMavenConfig(configSettings: com.intellij.execution.RunnerAndConfigurationSettings) {
        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        if (executor != null) {
            ExecutionUtil.runConfiguration(configSettings, executor)
        }
    }
}

class MavenVerifyAction : RunMavenGoalAction("verify", "Maven Verify", runSpotlessFirst = true)
class MavenTestAction : RunMavenGoalAction("test", "Maven Test", runSpotlessFirst = true)
class MavenCleanAction : RunMavenGoalAction("clean", "Maven Clean", runSpotlessFirst = true)
class SpotlessApplyAction : RunMavenGoalAction("spotless:apply", "Spotless Apply")
class MavenCompileAction : RunMavenGoalAction("compile", "Maven Compile", runSpotlessFirst = true)
