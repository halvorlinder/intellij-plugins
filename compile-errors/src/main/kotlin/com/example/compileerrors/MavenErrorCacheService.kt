package com.example.compileerrors

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class MavenErrorCacheService(private val project: Project) : Disposable {

    private var _errors: List<CompileErrorItem> = emptyList()
    private var _isRunning = false
    private val refreshListeners = mutableListOf<() -> Unit>()

    val errors: List<CompileErrorItem> get() = _errors
    var isRunning: Boolean
        get() = _isRunning
        set(value) {
            _isRunning = value
        }

    fun updateErrors(errors: List<CompileErrorItem>) {
        _errors = errors
        notifyListeners()
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

    override fun dispose() {
        synchronized(refreshListeners) { refreshListeners.clear() }
    }
}
