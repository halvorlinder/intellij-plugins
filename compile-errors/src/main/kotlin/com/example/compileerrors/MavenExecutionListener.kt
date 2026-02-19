package com.example.compileerrors

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import java.util.concurrent.ConcurrentHashMap

class MavenExecutionListener(private val project: Project) : ExecutionListener {

    private val trackedHandlers = ConcurrentHashMap<ProcessHandler, StringBuilder>()

    companion object {
        private val LOG = Logger.getInstance(MavenExecutionListener::class.java)
    }

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val isMaven = env.runProfile is MavenRunConfiguration
        LOG.info("processStarted: executorId=$executorId, runProfile=${env.runProfile::class.simpleName}, isMaven=$isMaven")

        if (!isMaven) return

        val service = project.getService(MavenErrorCacheService::class.java) ?: return
        val output = StringBuilder()
        trackedHandlers[handler] = output

        service.isRunning = true
        service.notifyListeners()

        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                output.append(event.text)
            }
        })
    }

    override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        val output = trackedHandlers.remove(handler) ?: return
        val service = project.getService(MavenErrorCacheService::class.java) ?: return

        val outputText = output.toString()
        LOG.info("processTerminated: exitCode=$exitCode, outputLength=${outputText.length}")

        val errors = MavenOutputParser.parse(outputText)
        LOG.info("Parsed ${errors.size} errors from Maven output")

        ApplicationManager.getApplication().invokeLater {
            service.updateErrors(errors)
            service.isRunning = trackedHandlers.isNotEmpty()
            service.notifyListeners()
        }
    }
}
