package com.example.commandpalette

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "CommandPaletteRecents", storages = [Storage("command-palette-recents.xml")])
class RecentCommandsService : PersistentStateComponent<RecentCommandsService.State> {

    data class State(var recentActionIds: MutableList<String> = mutableListOf())

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun getRecentActionIds(): List<String> = myState.recentActionIds.toList()

    fun recordUsage(actionId: String) {
        myState.recentActionIds.remove(actionId)
        myState.recentActionIds.add(0, actionId)
        if (myState.recentActionIds.size > MAX_RECENT) {
            myState.recentActionIds = myState.recentActionIds.take(MAX_RECENT).toMutableList()
        }
    }

    companion object {
        private const val MAX_RECENT = 50

        fun getInstance(): RecentCommandsService =
            ApplicationManager.getApplication().getService(RecentCommandsService::class.java)
    }
}
