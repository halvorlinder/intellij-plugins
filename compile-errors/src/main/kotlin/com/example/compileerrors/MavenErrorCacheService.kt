package com.example.compileerrors

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class MavenErrorCacheService(private val project: Project) : Disposable {

    private var _errors: List<CompileErrorItem> = emptyList()
    private var _isRunning = false
    private val refreshListeners = mutableListOf<() -> Unit>()
    private val modifiedSinceCompile: MutableSet<VirtualFile> = ConcurrentHashMap.newKeySet()

    val errors: List<CompileErrorItem> get() = _errors
    var isRunning: Boolean
        get() = _isRunning
        set(value) {
            _isRunning = value
        }

    init {
        val documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (_errors.any { it.virtualFile == file }) {
                    modifiedSinceCompile.add(file)
                }
            }
        }
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, this)

        project.messageBus.connect(this).subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                    if (!CompileErrorsSettings.getInstance(project).autoResolveEnabled) return
                    if (_errors.isEmpty()) return

                    val filesToCheck = fileEditors.mapNotNull { it.file }
                        .filter { it in modifiedSinceCompile }
                        .distinct()

                    if (filesToCheck.isNotEmpty()) {
                        checkResolvedErrors(filesToCheck)
                    }
                }
            }
        )
    }

    fun updateErrors(errors: List<CompileErrorItem>) {
        _errors = errors
        modifiedSinceCompile.clear()
        notifyListeners()
    }

    fun clearModifiedTracking() {
        modifiedSinceCompile.clear()
    }

    fun addRefreshListener(listener: () -> Unit) {
        synchronized(refreshListeners) { refreshListeners.add(listener) }
    }

    fun removeRefreshListener(listener: () -> Unit) {
        synchronized(refreshListeners) { refreshListeners.remove(listener) }
    }

    fun notifyListeners() {
        val listeners = synchronized(refreshListeners) { refreshListeners.toList() }
        for (listener in listeners) {
            listener()
        }
    }

    private fun checkResolvedErrors(filesToCheck: List<VirtualFile>) {
        ReadAction.nonBlocking<List<CompileErrorItem>> {
            val wolf = WolfTheProblemSolver.getInstance(project)
            val currentErrors = _errors.toList()
            val resolved = mutableSetOf<CompileErrorItem>()

            for (file in filesToCheck) {
                val errorsForFile = currentErrors.filter { it.virtualFile == file }
                if (errorsForFile.isEmpty()) continue

                if (!wolf.isProblemFile(file)) {
                    resolved.addAll(errorsForFile)
                    continue
                }

                for (error in errorsForFile) {
                    if (!hasErrorHighlightOnLine(error)) {
                        resolved.add(error)
                    }
                }
            }

            currentErrors.filter { it !in resolved }
        }
            .coalesceBy(this)
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.any()) { remaining ->
                if (remaining.size != _errors.size) {
                    _errors = remaining
                    notifyListeners()
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun hasErrorHighlightOnLine(error: CompileErrorItem): Boolean {
        val document = FileDocumentManager.getInstance().getDocument(error.virtualFile) ?: return true
        if (error.line >= document.lineCount) return false

        val lineStartOffset = document.getLineStartOffset(error.line)
        val lineEndOffset = document.getLineEndOffset(error.line)

        var found = false
        com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return true
        DaemonCodeAnalyzerEx.processHighlights(
            document, project, HighlightSeverity.ERROR, lineStartOffset, lineEndOffset
        ) { highlight ->
            if (highlight.severity == HighlightSeverity.ERROR) {
                found = true
                false // stop processing
            } else {
                true // continue
            }
        }
        return found
    }

    override fun dispose() {
        synchronized(refreshListeners) { refreshListeners.clear() }
    }
}
