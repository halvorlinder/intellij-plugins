package com.example.kotlintypeinfo

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
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
import com.intellij.ui.LightweightHint
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
import java.awt.Font

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
            val html = ReadAction.compute<String?, Throwable> {
                analyze(ktElement) {
                    tryResolveCall(leafElement)
                        ?: tryResolveReference(leafElement)
                        ?: tryExpressionType(leafElement)
                }
            }
            if (html != null) {
                ApplicationManager.getApplication().invokeLater {
                    if (editor.isDisposed) return@invokeLater
                    showHint(editor, html)
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
        return styledType(renderType(type))
    }

    // ── HTML Rendering ──────────────────────────────────────────

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
        val colors = resolveColors()

        return when (symbol) {
            is KaConstructorSymbol -> {
                val returnType = styledType(renderType(signature.returnType))
                val params = renderParamsHtml(signature, colors)
                "${colors.kw("constructor")} $returnType($params)"
            }
            else -> {
                val sb = StringBuilder()
                appendModifiersHtml(sb, symbol, colors)
                sb.append("${colors.kw("fun")} ")
                if (extensionReceiver != null) {
                    signature.receiverType?.let {
                        sb.append(styledType(renderType(it)))
                        sb.append(".")
                    }
                }
                val name = (symbol as? KaNamedFunctionSymbol)?.name?.asString() ?: "invoke"
                sb.append(name)
                sb.append("(")
                sb.append(renderParamsHtml(signature, colors))
                sb.append("): ")
                sb.append(styledType(renderType(signature.returnType)))
                sb.toString()
            }
        }
    }

    private fun KaSession.renderCallableSymbol(symbol: KaCallableSymbol): String {
        val colors = resolveColors()
        return when (symbol) {
            is KaPropertySymbol -> {
                val keyword = if (symbol.isVal) "val" else "var"
                val receiverPrefix = symbol.receiverParameter?.let {
                    styledType(renderType(symbol.returnType)) + "."
                } ?: ""
                "${colors.kw(keyword)} $receiverPrefix${symbol.name.asString()}: ${styledType(renderType(symbol.returnType))}"
            }
            is KaVariableSymbol -> {
                val keyword = if (symbol.isVal) "val" else "var"
                "${colors.kw(keyword)} ${symbol.name.asString()}: ${styledType(renderType(symbol.returnType))}"
            }
            else -> styledType(renderType(symbol.returnType))
        }
    }

    private fun KaSession.renderParamsHtml(signature: KaFunctionSignature<*>, colors: Colors): String {
        if (signature.valueParameters.isEmpty()) return ""
        val rendered = signature.valueParameters.map { p ->
            "${colors.param(p.name.asString())}: ${styledType(renderType(p.returnType))}"
        }
        val singleLine = rendered.joinToString(", ")
        // Check length without HTML tags for wrapping decision
        val plainLength = singleLine.replace(Regex("<[^>]*>"), "").length
        if (plainLength <= 60) return singleLine
        return "<br>" + rendered.joinToString(",<br>") { "&nbsp;&nbsp;&nbsp;&nbsp;$it" } + "<br>"
    }

    private fun appendModifiersHtml(sb: StringBuilder, symbol: KaFunctionSymbol, colors: Colors) {
        if (symbol is KaNamedFunctionSymbol) {
            val mods = mutableListOf<String>()
            when (symbol.visibility) {
                KaSymbolVisibility.PUBLIC -> mods.add("public")
                KaSymbolVisibility.PROTECTED -> mods.add("protected")
                KaSymbolVisibility.INTERNAL -> mods.add("internal")
                KaSymbolVisibility.PRIVATE -> mods.add("private")
                else -> {}
            }
            if (symbol.isInline) mods.add("inline")
            if (symbol.isSuspend) mods.add("suspend")
            if (symbol.isInfix) mods.add("infix")
            if (symbol.isOperator) mods.add("operator")
            if (symbol.isTailRec) mods.add("tailrec")
            if (symbol.isExternal) mods.add("external")
            for (mod in mods) {
                sb.append(colors.kw(mod))
                sb.append(" ")
            }
        }
    }

    private fun KaSession.renderType(type: KaType): String {
        return type.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)
    }

    /** Wraps type text so that type names get class-name color and punctuation stays default. */
    private fun styledType(typeText: String): String {
        val colors = resolveColors()
        val sb = StringBuilder()
        val buf = StringBuilder()

        fun flushIdent() {
            if (buf.isNotEmpty()) {
                sb.append(colors.type(buf.toString()))
                buf.setLength(0)
            }
        }

        for (ch in typeText) {
            when {
                ch.isLetterOrDigit() || ch == '_' || ch == '@' -> buf.append(ch)
                else -> {
                    flushIdent()
                    sb.append(esc(ch.toString()))
                }
            }
        }
        flushIdent()
        return sb.toString()
    }

    // ── Colors ──────────────────────────────────────────────────

    private data class Colors(
        val kwHex: String, val kwBold: Boolean,
        val typeHex: String?,
        val paramHex: String?
    ) {
        fun kw(text: String): String {
            val bold = if (kwBold) "font-weight:bold;" else ""
            return "<span style=\"color:$kwHex;$bold\">${esc(text)}</span>"
        }

        fun type(text: String): String {
            if (typeHex == null) return esc(text)
            return "<span style=\"color:$typeHex;\">${esc(text)}</span>"
        }

        fun param(text: String): String {
            if (paramHex == null) return esc(text)
            return "<span style=\"color:$paramHex;\">${esc(text)}</span>"
        }
    }

    private fun resolveColors(): Colors {
        val scheme = EditorColorsManager.getInstance().globalScheme

        fun colorOf(vararg keys: String): Color? {
            for (key in keys) {
                val c = scheme.getAttributes(TextAttributesKey.find(key))?.foregroundColor
                if (c != null) return c
            }
            return null
        }

        fun hex(c: Color?): String? = c?.let { String.format("#%02x%02x%02x", it.red, it.green, it.blue) }

        val kwColor = colorOf("KOTLIN_KEYWORD", "DEFAULT_KEYWORD") ?: Color(0xCC, 0x78, 0x32)
        val kwAttrs = scheme.getAttributes(TextAttributesKey.find("KOTLIN_KEYWORD"))
            ?: scheme.getAttributes(TextAttributesKey.find("DEFAULT_KEYWORD"))
        val kwBold = kwAttrs?.fontType?.let { it and Font.BOLD != 0 } ?: true

        val typeColor = colorOf(
            "KOTLIN_CLASS", "KOTLIN_TYPE_PARAMETER",
            "DEFAULT_CLASS_NAME", "DEFAULT_INTERFACE_NAME"
        )
        val paramColor = colorOf("KOTLIN_PARAMETER", "DEFAULT_PARAMETER")

        return Colors(
            kwHex = hex(kwColor)!!,
            kwBold = kwBold,
            typeHex = hex(typeColor),
            paramHex = hex(paramColor)
        )
    }

    // ── Hint display ────────────────────────────────────────────

    private fun showHint(editor: Editor, html: String) {
        val scheme = editor.colorsScheme
        val font = scheme.editorFontName
        val size = scheme.editorFontSize
        val wrapped = "<span style=\"font-family:'$font',monospace;font-size:${size}pt;\">$html</span>"
        val label = HintUtil.createInformationLabel(wrapped)
        val hint = LightweightHint(label)
        val hintManager = HintManager.getInstance() as HintManagerImpl
        val position = hintManager.getHintPosition(hint, editor, HintManager.ABOVE)
        val flags = HintManager.HIDE_BY_ANY_KEY or
                HintManager.HIDE_BY_TEXT_CHANGE or
                HintManager.HIDE_BY_SCROLLING
        hintManager.showEditorHint(hint, editor, position, flags, 0, false)
    }

    companion object {
        fun esc(text: String): String =
            text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
