package com.example.kotlintypeinfo

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

@OptIn(KaExperimentalApi::class)
class ShowResolvedTypeInfoAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = project != null && editor != null && file is KtFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return
        val offset = editor.caretModel.offset

        val leafElement = file.findElementAt(offset) ?: run {
            showHint(editor, "No type info available")
            return
        }

        val ktElement = PsiTreeUtil.getParentOfType(leafElement, KtElement::class.java, false)
            ?: run {
                showHint(editor, "No type info available")
                return
            }

        ApplicationManager.getApplication().executeOnPooledThread {
            val text = ReadAction.compute<String?, Throwable> {
                analyze(ktElement) {
                    tryResolveCall(leafElement)
                        ?: tryResolveReference(leafElement)
                        ?: tryExpressionType(leafElement)
                }
            }
            ApplicationManager.getApplication().invokeLater {
                showHint(editor, text ?: "No type info available")
            }
        }
    }

    private fun KaSession.tryResolveCall(element: PsiElement): String? {
        // Find the nearest call-like expression
        val callExpr = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java, false)
            ?: PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java, false)
            ?: PsiTreeUtil.getParentOfType(element, KtBinaryExpression::class.java, false)
            ?: PsiTreeUtil.getParentOfType(element, KtUnaryExpression::class.java, false)
            ?: return null

        // For dot-qualified expressions, try resolving the selector first
        val exprToResolve: KtElement = if (callExpr is KtDotQualifiedExpression) {
            callExpr.selectorExpression ?: callExpr
        } else {
            callExpr
        }

        val funcCall = exprToResolve.resolveToCall()?.successfulFunctionCallOrNull()
            ?: callExpr.resolveToCall()?.successfulFunctionCallOrNull()
            ?: return null

        return renderFunctionCall(funcCall)
    }

    private fun KaSession.tryResolveReference(element: PsiElement): String? {
        val refExpr = PsiTreeUtil.getParentOfType(element, KtReferenceExpression::class.java)
            ?: return null

        // Try as function call first
        val funcCall = refExpr.resolveToCall()?.successfulFunctionCallOrNull()
        if (funcCall != null) {
            return renderFunctionCall(funcCall)
        }

        // Fall back to reference resolution
        val symbol = refExpr.mainReference.resolveToSymbol()
        if (symbol is KaCallableSymbol) {
            return renderCallableSymbol(symbol)
        }

        return null
    }

    private fun KaSession.tryExpressionType(element: PsiElement): String? {
        val expression = PsiTreeUtil.getParentOfType(element, KtExpression::class.java)
            ?: return null
        val type = expression.expressionType ?: return null
        return renderType(type)
    }

    private fun KaSession.renderFunctionCall(funcCall: KaFunctionCall<*>): String {
        val signature = funcCall.partiallyAppliedSymbol.signature
        val symbol = funcCall.partiallyAppliedSymbol.symbol
        val extensionReceiver = funcCall.partiallyAppliedSymbol.extensionReceiver

        return when (symbol) {
            is KaConstructorSymbol -> {
                val returnType = renderType(signature.returnType)
                val params = signature.valueParameters.joinToString(", ") { p ->
                    "${p.name}: ${renderType(p.returnType)}"
                }
                "constructor $returnType($params)"
            }
            else -> {
                val receiverPrefix = if (extensionReceiver != null) {
                    signature.receiverType?.let { "${renderType(it)}." } ?: ""
                } else ""
                val name = (symbol as? KaNamedFunctionSymbol)?.name ?: "invoke"
                val params = signature.valueParameters.joinToString(", ") { p ->
                    "${p.name}: ${renderType(p.returnType)}"
                }
                val returnType = renderType(signature.returnType)
                "fun ${receiverPrefix}$name($params): $returnType"
            }
        }
    }

    private fun KaSession.renderCallableSymbol(symbol: KaCallableSymbol): String {
        return when (symbol) {
            is KaPropertySymbol -> {
                val keyword = if (symbol.isVal) "val" else "var"
                val receiverPrefix = symbol.receiverParameter?.let {
                    "${renderType(symbol.returnType)}."
                } ?: ""
                "$keyword ${receiverPrefix}${symbol.name}: ${renderType(symbol.returnType)}"
            }
            is KaVariableSymbol -> {
                val keyword = if (symbol.isVal) "val" else "var"
                "$keyword ${symbol.name}: ${renderType(symbol.returnType)}"
            }
            else -> renderType(symbol.returnType)
        }
    }

    private fun KaSession.renderType(type: KaType): String {
        return type.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)
    }

    private fun showHint(editor: Editor, text: String) {
        HintManager.getInstance().showInformationHint(editor, text)
    }
}
