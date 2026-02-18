package com.example.compileerrors

import com.example.sharedui.PreviewItem
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.vfs.VirtualFile

data class CompileErrorItem(
    override val virtualFile: VirtualFile,
    override val line: Int,
    override val column: Int,
    val message: String,
    val category: CompilerMessageCategory
) : PreviewItem
