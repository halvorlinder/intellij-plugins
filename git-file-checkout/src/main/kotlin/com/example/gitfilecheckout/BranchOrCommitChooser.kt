package com.example.gitfilecheckout

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import git4idea.repo.GitRepository
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

object BranchOrCommitChooser {

    fun show(project: Project, repo: GitRepository, onSelected: (String) -> Unit) {
        val allBranches = collectBranches(repo)

        val listModel = DefaultListModel<String>()
        allBranches.forEach { listModel.addElement(it) }

        val branchList = JBList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            if (listModel.size() > 0) selectedIndex = 0
        }

        val searchField = SearchTextField(false).apply {
            textEditor.emptyText.text = "Branch name or commit hash..."
        }

        val scrollPane = JBScrollPane(branchList)

        val panel = JPanel(BorderLayout()).apply {
            add(searchField, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            preferredSize = Dimension(400, 350)
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, searchField.textEditor)
            .setTitle("Checkout File from Branch/Commit")
            .setFocusable(true)
            .setRequestFocus(true)
            .setMovable(true)
            .setResizable(true)
            .createPopup()

        // Filter list as user types
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val filter = searchField.text.lowercase()
                listModel.clear()
                allBranches
                    .filter { it.lowercase().contains(filter) }
                    .forEach { listModel.addElement(it) }
                if (listModel.size() > 0) {
                    branchList.selectedIndex = 0
                }
            }
        })

        // Keyboard handling on the search field
        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val selected = branchList.selectedValue ?: searchField.text.trim()
                        if (selected.isNotEmpty()) {
                            popup.cancel()
                            onSelected(selected)
                        }
                    }
                    KeyEvent.VK_DOWN -> {
                        if (listModel.size() > 0) {
                            branchList.requestFocusInWindow()
                            if (branchList.selectedIndex < 0) {
                                branchList.selectedIndex = 0
                            }
                        }
                    }
                    KeyEvent.VK_ESCAPE -> popup.cancel()
                }
            }
        })

        // Keyboard handling on the list
        branchList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val selected = branchList.selectedValue
                        if (selected != null) {
                            popup.cancel()
                            onSelected(selected)
                        }
                    }
                    KeyEvent.VK_UP -> {
                        if (branchList.selectedIndex == 0) {
                            searchField.textEditor.requestFocusInWindow()
                        }
                    }
                }
            }
        })

        // Double-click on list item
        branchList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    val selected = branchList.selectedValue
                    if (selected != null) {
                        popup.cancel()
                        onSelected(selected)
                    }
                }
            }
        })

        popup.showCenteredInCurrentWindow(project)
    }

    private fun collectBranches(repo: GitRepository): List<String> {
        val localBranches = repo.branches.localBranches
            .map { it.name }
            .sortedWith(compareBy<String> { it != "main" && it != "master" }.thenBy { it })

        val remoteBranches = repo.branches.remoteBranches
            .map { it.name }
            .sorted()

        return localBranches + remoteBranches
    }
}
