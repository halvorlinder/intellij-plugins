package com.example.vscodewindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class PanelStateService(private val project: Project) {

    private val lastShownByAnchor = ConcurrentHashMap<ToolWindowAnchor, String>()

    init {
        lastShownByAnchor[ToolWindowAnchor.LEFT] = "Project"
        lastShownByAnchor[ToolWindowAnchor.BOTTOM] = "Terminal"
    }

    fun getLastShown(anchor: ToolWindowAnchor): String? = lastShownByAnchor[anchor]

    fun setLastShown(anchor: ToolWindowAnchor, id: String) {
        lastShownByAnchor[anchor] = id
    }

    class ToolWindowTracker(private val project: Project) : ToolWindowManagerListener {
        override fun toolWindowShown(toolWindow: com.intellij.openapi.wm.ToolWindow) {
            val service = project.getService(PanelStateService::class.java) ?: return
            val anchor = toolWindow.anchor
            service.setLastShown(anchor, toolWindow.id)
        }
    }

    companion object {
        fun getInstance(project: Project): PanelStateService =
            project.getService(PanelStateService::class.java)

        fun getToolWindowsAtAnchor(project: Project, anchor: ToolWindowAnchor): List<com.intellij.openapi.wm.ToolWindow> {
            val twm = ToolWindowManager.getInstance(project)
            return twm.toolWindowIds.mapNotNull { id ->
                twm.getToolWindow(id)?.takeIf { it.anchor == anchor && it.isAvailable }
            }
        }
    }
}
