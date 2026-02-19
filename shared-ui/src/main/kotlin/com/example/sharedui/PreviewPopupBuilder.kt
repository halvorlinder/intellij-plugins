package com.example.sharedui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.Dimension
import java.awt.event.KeyEvent

object PreviewPopupBuilder {

    fun <T : PreviewItem> show(
        project: Project,
        panel: DualPanePreviewPanel<T>,
        minSize: Dimension = Dimension(1000, 600),
        onNavigate: (T) -> Unit,
        onCancel: (() -> Unit)? = null
    ): JBPopup {
        val popup: JBPopup

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel.list)
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setCancelKeyEnabled(true)
            .setMinSize(minSize)
            .setCancelCallback {
                onCancel?.invoke()
                panel.dispose()
                true
            }
            .createPopup()

        panel.addKeyHandler { e ->
            if (e.keyCode == KeyEvent.VK_ENTER) {
                val selected = panel.selectedItem ?: return@addKeyHandler false
                popup.cancel()
                onNavigate(selected)
                true
            } else false
        }

        popup.showCenteredInCurrentWindow(project)
        return popup
    }
}
