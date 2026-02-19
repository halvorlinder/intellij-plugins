package com.example.usagepreview

import com.example.sharedui.DualPanePreviewPanel
import com.example.sharedui.PreviewItem
import com.example.sharedui.PreviewPopupBuilder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.searches.ReferencesSearch
import java.awt.Color

data class UsageItem(
    override val virtualFile: VirtualFile,
    override val line: Int,
    override val column: Int,
    val offset: Int,
    val presentableText: String,
    val lineText: String,
    val isDeclaration: Boolean = false
) : PreviewItem

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

        if (resolvedElement is PsiNameIdentifierOwner) {
            val nameId = resolvedElement.nameIdentifier
            if (nameId != null) {
                return nameId.textRange.containsOffset(offset) ||
                        elementAtCaret.textRange == nameId.textRange
            }
        }

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
                    val lineText = declDoc.getText(TextRange(lineStart, lineEnd)).trim()
                    usages.add(
                        UsageItem(
                            virtualFile = declFile,
                            line = declLine,
                            column = 0,
                            offset = declOffset,
                            presentableText = "${declFile.name}:${declLine + 1}",
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
                    val lineText = doc.getText(TextRange(lineStart, lineEnd)).trim()
                    usages.add(
                        UsageItem(
                            virtualFile = file,
                            line = line,
                            column = 0,
                            offset = offset,
                            presentableText = "${file.name}:${line + 1}",
                            lineText = lineText
                        )
                    )
                }
            }

            if (usages.isEmpty()) {
                ApplicationManager.getApplication().invokeLater {
                    HintManager.getInstance().showErrorHint(editor, "No usages found")
                }
                return
            }

            ApplicationManager.getApplication().invokeLater {
                showPopup(project, usages)
            }
        }
    }.queue()
}

private fun showPopup(project: Project, usages: List<UsageItem>) {
    val panel = DualPanePreviewPanel(
        project,
        usages,
        UsageCellRenderer(),
        highlightColorProvider = { item ->
            if (item.isDeclaration) Color(30, 100, 30, 50) else null
        },
        treeItemText = { item ->
            val text = item.lineText.let { if (it.length > 50) it.take(50) + "..." else it }
            "L${item.line + 1}: $text"
        },
        itemMarker = { it.isDeclaration }
    )

    PreviewPopupBuilder.show(
        project,
        panel,
        onNavigate = { usage ->
            OpenFileDescriptor(project, usage.virtualFile, usage.line, 0).navigate(true)
        }
    )
}
