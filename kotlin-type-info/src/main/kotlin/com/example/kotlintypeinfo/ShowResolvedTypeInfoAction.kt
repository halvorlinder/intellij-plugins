package com.example.kotlintypeinfo

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance
import java.awt.Color
import javax.swing.JComponent

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

        val leafElement = file.findElementAt(offset) ?: return
        val ktElement = PsiTreeUtil.getParentOfType(leafElement, KtElement::class.java, false) ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val text = ReadAction.compute<String?, Throwable> {
                analyze(ktElement) {
                    tryResolveCall(leafElement)
                        ?: tryResolveReference(leafElement)
                        ?: tryExpressionType(leafElement)
                }
            }
            if (text != null) {
                ApplicationManager.getApplication().invokeLater {
                    if (editor.isDisposed) return@invokeLater
                    showStyledHint(editor, text)
                }
            }
        }
    }

    // ── Resolution ──────────────────────────────────────────────

    private fun KaSession.tryResolveCall(element: PsiElement): String? {
        val callExpr = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java, false)
            ?: PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java, false)
            ?: PsiTreeUtil.getParentOfType(element, KtBinaryExpression::class.java, false)
            ?: PsiTreeUtil.getParentOfType(element, KtUnaryExpression::class.java, false)
            ?: return null

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

        val funcCall = refExpr.resolveToCall()?.successfulFunctionCallOrNull()
        if (funcCall != null) return renderFunctionCall(funcCall)

        val symbol = refExpr.mainReference.resolveToSymbol()
        if (symbol is KaCallableSymbol) return renderCallableSymbol(symbol)

        return null
    }

    private fun KaSession.tryExpressionType(element: PsiElement): String? {
        val expression = PsiTreeUtil.getParentOfType(element, KtExpression::class.java) ?: return null
        val type = expression.expressionType ?: return null
        return renderType(type)
    }

    // ── Rendering ───────────────────────────────────────────────

    private fun KaSession.renderFunctionCall(funcCall: KaFunctionCall<*>): String {
        val typeArgMapping = funcCall.typeArgumentsMapping
        val signature: KaFunctionSignature<*> = if (typeArgMapping.isNotEmpty()) {
            val substitutor = createSubstitutor(typeArgMapping)
            funcCall.partiallyAppliedSymbol.signature.substitute(substitutor)
        } else {
            funcCall.partiallyAppliedSymbol.signature
        }
        val symbol = funcCall.partiallyAppliedSymbol.symbol
        val extensionReceiver = funcCall.partiallyAppliedSymbol.extensionReceiver

        return when (symbol) {
            is KaConstructorSymbol -> {
                val returnType = renderType(signature.returnType)
                val params = renderParams(signature)
                "constructor $returnType($params)"
            }
            else -> {
                val modifiers = buildModifiers(symbol)
                val receiverPrefix = if (extensionReceiver != null) {
                    signature.receiverType?.let { renderType(it) + "." } ?: ""
                } else ""
                val name = (symbol as? KaNamedFunctionSymbol)?.name?.asString() ?: "invoke"
                val params = renderParams(signature)
                val returnType = renderType(signature.returnType)
                "${modifiers}fun $receiverPrefix$name($params): $returnType"
            }
        }
    }

    private fun KaSession.renderCallableSymbol(symbol: KaCallableSymbol): String {
        return when (symbol) {
            is KaPropertySymbol -> {
                val keyword = if (symbol.isVal) "val" else "var"
                val receiverPrefix = symbol.receiverParameter?.let {
                    renderType(symbol.returnType) + "."
                } ?: ""
                "$keyword $receiverPrefix${symbol.name.asString()}: ${renderType(symbol.returnType)}"
            }
            is KaVariableSymbol -> {
                val keyword = if (symbol.isVal) "val" else "var"
                "$keyword ${symbol.name.asString()}: ${renderType(symbol.returnType)}"
            }
            else -> renderType(symbol.returnType)
        }
    }

    private fun KaSession.renderParams(signature: KaFunctionSignature<*>): String {
        if (signature.valueParameters.isEmpty()) return ""
        val rendered = signature.valueParameters.map { p ->
            "${p.name.asString()}: ${renderType(p.returnType)}"
        }
        val singleLine = rendered.joinToString(", ")
        if (singleLine.length <= 50) return singleLine
        return "\n" + rendered.joinToString(",\n") { "    $it" } + "\n"
    }

    private fun buildModifiers(symbol: KaFunctionSymbol): String {
        val parts = mutableListOf<String>()
        if (symbol is KaNamedFunctionSymbol) {
            when (symbol.visibility) {
                KaSymbolVisibility.PUBLIC -> parts.add("public")
                KaSymbolVisibility.PROTECTED -> parts.add("protected")
                KaSymbolVisibility.INTERNAL -> parts.add("internal")
                KaSymbolVisibility.PRIVATE -> parts.add("private")
                else -> {}
            }
            if (symbol.isInline) parts.add("inline")
            if (symbol.isSuspend) parts.add("suspend")
            if (symbol.isInfix) parts.add("infix")
            if (symbol.isOperator) parts.add("operator")
            if (symbol.isTailRec) parts.add("tailrec")
            if (symbol.isExternal) parts.add("external")
        }
        return if (parts.isEmpty()) "" else parts.joinToString(" ") + " "
    }

    private fun KaSession.renderType(type: KaType): String {
        return type.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)
    }

    // ── Hint display ────────────────────────────────────────────

    private val KOTLIN_KEYWORDS = setOf(
        "fun", "val", "var", "public", "private", "protected", "internal",
        "inline", "suspend", "infix", "operator", "tailrec", "external",
        "override", "open", "abstract", "sealed", "data", "class", "object",
        "constructor", "return", "if", "else", "when", "for", "while",
        "throw", "try", "catch", "finally", "vararg", "crossinline", "noinline",
        "reified", "companion", "enum", "annotation", "interface"
    )

    private fun showStyledHint(editor: Editor, text: String) {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val kwKey = TextAttributesKey.find("KOTLIN_KEYWORD")
        val kwColor = scheme.getAttributes(kwKey)?.foregroundColor
            ?: scheme.getAttributes(TextAttributesKey.find("DEFAULT_KEYWORD"))?.foregroundColor
            ?: Color(0xCC, 0x78, 0x32)
        val font = scheme.editorFontName
        val fontSize = scheme.editorFontSize

        val html = buildHtml(text, kwColor, font, fontSize)
        val label = HintUtil.createInformationLabel(html) as JComponent
        HintManager.getInstance().showInformationHint(editor, label)
    }

    private fun buildHtml(text: String, kwColor: Color, font: String, fontSize: Int): String {
        val kwHex = String.format("#%02x%02x%02x", kwColor.red, kwColor.green, kwColor.blue)
        val sb = StringBuilder()
        sb.append("<html><body style='font-family:\"$font\",monospace;font-size:${fontSize}pt;white-space:pre;'>")

        for (line in text.split('\n')) {
            // Tokenize: split on word boundaries, preserving delimiters
            val tokens = line.split(Regex("(?<=\\b)|(?=\\b)"))
            for (token in tokens) {
                if (token in KOTLIN_KEYWORDS) {
                    sb.append("<span style='color:$kwHex;font-weight:bold;'>")
                    sb.append(escapeHtml(token))
                    sb.append("</span>")
                } else {
                    sb.append(escapeHtml(token))
                }
            }
            sb.append("<br>")
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
