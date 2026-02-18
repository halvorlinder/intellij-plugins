package com.example.sharedui

import com.intellij.openapi.vfs.VirtualFile

interface PreviewItem {
    val virtualFile: VirtualFile
    val line: Int
    val column: Int
}
