package com.example.compileerrors

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

@Service(Service.Level.PROJECT)
class CompileErrorsCacheService(private val project: Project) : Disposable {

    private val connection = project.messageBus.connect(this)
    private var _errors: List<CompileErrorItem> = emptyList()
    private var _compiling = false
    private val refreshListeners = mutableListOf<() -> Unit>()

    val errors: List<CompileErrorItem> get() = _errors
    val isCompiling: Boolean get() = _compiling

    init {
        connection.subscribe(CompilerTopics.COMPILATION_STATUS, object : CompilationStatusListener {
            override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
                val items = mutableListOf<CompileErrorItem>()

                for (category in listOf(CompilerMessageCategory.ERROR, CompilerMessageCategory.WARNING)) {
                    val messages = compileContext.getMessages(category)
                    for (msg in messages) {
                        val file = msg.virtualFile ?: continue
                        var line = 0
                        var col = 0

                        val navigatable = msg.navigatable
                        if (navigatable is OpenFileDescriptor) {
                            line = navigatable.line
                            col = navigatable.column
                        } else if (msg is CompilerMessageImpl) {
                            // Some messages store line/column directly
                            val msgLine = msg.line
                            val msgCol = msg.column
                            if (msgLine > 0) line = msgLine - 1 // Convert 1-based to 0-based
                            if (msgCol > 0) col = msgCol - 1
                        }

                        items.add(
                            CompileErrorItem(
                                virtualFile = file,
                                line = line,
                                column = col,
                                message = msg.message,
                                category = category
                            )
                        )
                    }
                }

                // Sort: errors first, then by file path, then by line
                items.sortWith(compareBy<CompileErrorItem> {
                    if (it.category == CompilerMessageCategory.ERROR) 0 else 1
                }.thenBy { it.virtualFile.path }.thenBy { it.line })

                _errors = items
                _compiling = false

                val listeners = synchronized(refreshListeners) { refreshListeners.toList() }
                for (listener in listeners) {
                    listener()
                }
            }
        })
    }

    fun triggerCompile() {
        if (_compiling) return
        _compiling = true

        // Notify listeners about compilation starting (so status can update)
        val listeners = synchronized(refreshListeners) { refreshListeners.toList() }
        for (listener in listeners) {
            listener()
        }

        CompilerManager.getInstance(project).make(null)
    }

    fun addRefreshListener(listener: () -> Unit) {
        synchronized(refreshListeners) { refreshListeners.add(listener) }
    }

    fun removeRefreshListener(listener: () -> Unit) {
        synchronized(refreshListeners) { refreshListeners.remove(listener) }
    }

    override fun dispose() {
        synchronized(refreshListeners) { refreshListeners.clear() }
    }
}
