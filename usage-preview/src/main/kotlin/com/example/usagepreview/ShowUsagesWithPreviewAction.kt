package com.example.usagepreview

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.searches.ReferencesSearch
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

data class UsageItem(
    val virtualFile: VirtualFile,
    val line: Int,
    val offset: Int,
    val presentableText: String,
    val lineText: String,
    val isDeclaration: Boolean = false
)

/**
 * gd action: If on a definition, show usages popup. If on a reference, jump to definition.
 */
class ShowUsagesWithPreviewAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val element = ReadAction.compute<PsiElement?, Throwable> {
            TargetElementUtil.findTargetElement(
                editor,
                TargetElementUtil.ELEMENT_NAME_ACCEPTED or
                        TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or
                        TargetElementUtil.LOOKUP_ITEM_ACCEPTED
            )
        } ?: run {
            HintManager.getInstance().showErrorHint(editor, "No symbol under caret")
            return
        }

        val isOnDefinition = ReadAction.compute<Boolean, Throwable> {
            isCaretOnDefinition(editor, element)
        }

        if (!isOnDefinition) {
            // Jump to definition
            val navFile = ReadAction.compute<VirtualFile?, Throwable> { element.containingFile?.virtualFile }
            val navOffset = ReadAction.compute<Int, Throwable> { element.textOffset }
            if (navFile != null) {
                OpenFileDescriptor(project, navFile, navOffset).navigate(true)
            }
            return
        }

        // On a definition — show usages popup
        findAndShowUsages(project, editor, element)
    }

    private fun isCaretOnDefinition(editor: Editor, resolvedElement: PsiElement): Boolean {
        val offset = editor.caretModel.offset
        val psiFile = PsiDocumentManager.getInstance(resolvedElement.project).getPsiFile(editor.document) ?: return false
        val elementAtCaret = psiFile.findElementAt(offset) ?: return false

        // Check if the resolved element's name identifier is at/near the caret
        if (resolvedElement is PsiNameIdentifierOwner) {
            val nameId = resolvedElement.nameIdentifier
            if (nameId != null) {
                return nameId.textRange.containsOffset(offset) ||
                        elementAtCaret.textRange == nameId.textRange
            }
        }

        // Fallback: check if resolved element itself contains the caret
        return resolvedElement.textRange.containsOffset(offset) &&
                resolvedElement.containingFile?.virtualFile ==
                PsiDocumentManager.getInstance(resolvedElement.project).getPsiFile(editor.document)?.virtualFile
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }
}

/**
 * gu action: Always show usages popup regardless of whether caret is on definition or reference.
 */
class ShowUsagesAlwaysAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val element = ReadAction.compute<PsiElement?, Throwable> {
            TargetElementUtil.findTargetElement(
                editor,
                TargetElementUtil.ELEMENT_NAME_ACCEPTED or
                        TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or
                        TargetElementUtil.LOOKUP_ITEM_ACCEPTED
            )
        } ?: run {
            HintManager.getInstance().showErrorHint(editor, "No symbol under caret")
            return
        }

        findAndShowUsages(project, editor, element)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }
}

internal fun findAndShowUsages(project: Project, editor: Editor, element: PsiElement) {
    object : Task.Backgroundable(project, "Finding Usages...", true) {
        override fun run(indicator: ProgressIndicator) {
            val usages = mutableListOf<UsageItem>()

            // Add the declaration itself as the first item
            ReadAction.run<Throwable> {
                val declFile = element.containingFile?.virtualFile
                val declDoc = element.containingFile?.let {
                    PsiDocumentManager.getInstance(project).getDocument(it)
                }
                if (declFile != null && declDoc != null) {
                    val declOffset = element.textOffset
                    val declLine = declDoc.getLineNumber(declOffset)
                    val lineStart = declDoc.getLineStartOffset(declLine)
                    val lineEnd = declDoc.getLineEndOffset(declLine)
                    val lineText = declDoc.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd)).trim()
                    usages.add(
                        UsageItem(
                            virtualFile = declFile,
                            line = declLine,
                            offset = declOffset,
                            presentableText = "${declFile.name}:${declLine + 1} (declaration)",
                            lineText = lineText,
                            isDeclaration = true
                        )
                    )
                }
            }

            // Find all references
            val references = ReadAction.compute<Collection<com.intellij.psi.PsiReference>, Throwable> {
                ReferencesSearch.search(element).findAll()
            }

            ReadAction.run<Throwable> {
                for (ref in references) {
                    indicator.checkCanceled()
                    val refElement = ref.element
                    val file = refElement.containingFile?.virtualFile ?: continue
                    val doc = refElement.containingFile?.let {
                        PsiDocumentManager.getInstance(project).getDocument(it)
                    } ?: continue
                    val offset = refElement.textOffset
                    val line = doc.getLineNumber(offset)
                    val lineStart = doc.getLineStartOffset(line)
                    val lineEnd = doc.getLineEndOffset(line)
                    val lineText = doc.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd)).trim()
                    usages.add(
                        UsageItem(
                            virtualFile = file,
                            line = line,
                            offset = offset,
                            presentableText = "${file.name}:${line + 1}",
                            lineText = lineText
                        )
                    )
                }
            }

            if (usages.isEmpty()) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    HintManager.getInstance().showErrorHint(editor, "No usages found")
                }
                return
            }

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                showPopup(project, usages)
            }
        }
    }.queue()
}

private fun showPopup(project: Project, usages: List<UsageItem>) {
    var popup: JBPopup? = null

    val panel = UsagePreviewPanel(project, usages) { usage ->
        popup?.cancel()
        OpenFileDescriptor(project, usage.virtualFile, usage.line, 0).navigate(true)
    }

    popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, panel.list)
        .setMovable(true)
        .setResizable(true)
        .setRequestFocus(true)
        .setFocusable(true)
        .setCancelOnClickOutside(true)
        .setCancelOnOtherWindowOpen(true)
        .setCancelKeyEnabled(true)
        .setMinSize(Dimension(700, 400))
        .setCancelCallback {
            panel.dispose()
            true
        }
        .createPopup()

    // Add Enter key handler to the list
    panel.list.addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_ENTER) {
                val selected = panel.list.selectedValue ?: return
                popup.cancel()
                OpenFileDescriptor(project, selected.virtualFile, selected.line, 0).navigate(true)
            }
        }
    })

    // Show centered in the IDE window
    popup.showCenteredInCurrentWindow(project)
}
