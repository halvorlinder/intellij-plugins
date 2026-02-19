package com.example.compileerrors

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "CompileErrorsSettings", storages = [Storage("compileErrors.xml")])
class CompileErrorsSettings : PersistentStateComponent<CompileErrorsSettings.State> {

    data class State(var autoResolveEnabled: Boolean = true)

    private var myState = State()

    var autoResolveEnabled: Boolean
        get() = myState.autoResolveEnabled
        set(value) { myState.autoResolveEnabled = value }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): CompileErrorsSettings =
            project.getService(CompileErrorsSettings::class.java)
    }
}
