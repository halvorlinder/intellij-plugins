package com.example.compileerrors

import com.intellij.openapi.vfs.LocalFileSystem

object MavenOutputParser {

    // --- Kotlin build tools (modern): [ERROR] file:///path:line:col message ---
    private val FILE_URI_ERROR = Regex("""^\[ERROR]\s+file:///(\S+\.\w+):(\d+):(\d+)\s+(.+)$""")
    private val FILE_URI_WARNING = Regex("""^\[WARNING]\s+file:///(\S+\.\w+):(\d+):(\d+)\s+(.+)$""")

    // --- Kotlin CLI wrapped in [ERROR]: [ERROR] e: /path:line:col: error: msg ---
    private val MAVEN_KOTLIN_CLI_ERROR = Regex("""^\[ERROR]\s+e:\s+(?:file://)?(/\S+\.\w+):(\d+):(\d+):\s+\w+:\s+(.+)$""")

    // --- Kotlin legacy (MavenPluginLogMessageCollector): [ERROR] /path: (line, col) msg ---
    private val MAVEN_PAREN_ERROR = Regex("""^\[ERROR]\s+(/\S+\.\w+):\s*\((\d+),\s*(\d+)\)\s+(.+)$""")
    private val MAVEN_PAREN_WARNING = Regex("""^\[WARNING]\s+(/\S+\.\w+):\s*\((\d+),\s*(\d+)\)\s+(.+)$""")

    // --- Java compiler (plexus-compiler): [ERROR] /path:[line,col] msg ---
    private val MAVEN_BRACKET_LC_ERROR = Regex("""^\[ERROR]\s+(/\S+\.\w+):\[(\d+),(\d+)]\s+(.+)$""")
    private val MAVEN_BRACKET_LC_WARNING = Regex("""^\[WARNING]\s+(/\S+\.\w+):\[(\d+),(\d+)]\s+(.+)$""")

    // --- Java compiler, line only: [ERROR] /path:[line] msg ---
    private val MAVEN_BRACKET_L_ERROR = Regex("""^\[ERROR]\s+(/\S+\.\w+):\[(\d+)]\s+(.+)$""")
    private val MAVEN_BRACKET_L_WARNING = Regex("""^\[WARNING]\s+(/\S+\.\w+):\[(\d+)]\s+(.+)$""")

    // --- Kotlin e:/w: standalone (not wrapped in [ERROR]) ---
    private val KOTLIN_E_FILE_URI = Regex("""^e:\s+file:///(\S+\.\w+):(\d+):(\d+)\s+(.+)$""")
    private val KOTLIN_W_FILE_URI = Regex("""^w:\s+file:///(\S+\.\w+):(\d+):(\d+)\s+(.+)$""")
    private val KOTLIN_E_COLON = Regex("""^e:\s+(/\S+\.\w+):(\d+):(\d+)\s+(.+)$""")
    private val KOTLIN_W_COLON = Regex("""^w:\s+(/\S+\.\w+):(\d+):(\d+)\s+(.+)$""")
    private val KOTLIN_E_PAREN = Regex("""^e:\s+(/\S+\.\w+):\s*\((\d+),\s*(\d+)\):\s*(.+)$""")

    // --- Spotless lint: [ERROR] FileName.kt:L36 step(code) error: msg ---
    // Also handles line ranges: FileName.kt:L10-15
    private val SPOTLESS_ERROR = Regex("""^\[ERROR]\s+(\S+\.\w+):L(\d+)(?:-\d+)?\s+.+\berror:\s+(.+)$""")
    // Spotless full path: Unable to format file /full/path/to/File.kt
    private val SPOTLESS_PATH = Regex("""Unable to format file\s+(/\S+)""")

    private data class ParsedError(val filePath: String, val line: Int, val col: Int, val message: String, val severity: ErrorSeverity)

    fun parse(output: String): List<CompileErrorItem> {
        val items = mutableListOf<CompileErrorItem>()
        val fileSystem = LocalFileSystem.getInstance()
        val lines = output.lines()

        // First pass: build filename → full path map from Spotless "Unable to format file" lines
        val filePathMap = mutableMapOf<String, String>()
        for (line in lines) {
            SPOTLESS_PATH.find(line)?.let { match ->
                val fullPath = match.groupValues[1]
                val fileName = fullPath.substringAfterLast('/')
                filePathMap[fileName] = fullPath
            }
        }

        // Second pass: parse all error patterns
        val seen = mutableSetOf<String>()
        for (line in lines) {
            val parsed = tryMatchCompilerError(line) ?: tryMatchSpotlessError(line, filePathMap) ?: continue
            val virtualFile = fileSystem.findFileByPath(parsed.filePath) ?: continue
            // Deduplicate (Maven sometimes prints errors twice: once inline, once in summary)
            val key = "${parsed.filePath}:${parsed.line}:${parsed.message}"
            if (!seen.add(key)) continue
            items.add(
                CompileErrorItem(
                    virtualFile = virtualFile,
                    line = (parsed.line - 1).coerceAtLeast(0),
                    column = (parsed.col - 1).coerceAtLeast(0),
                    message = parsed.message,
                    severity = parsed.severity
                )
            )
        }

        items.sortWith(
            compareBy<CompileErrorItem> { if (it.severity == ErrorSeverity.ERROR) 0 else 1 }
                .thenBy { it.virtualFile.path }
                .thenBy { it.line }
        )

        return items
    }

    private fun tryMatchCompilerError(line: String): ParsedError? {
        // Kotlin build tools file:// URI (most common for Kotlin 2.x via Maven)
        FILE_URI_ERROR.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError("/$path", l.toInt(), c.toInt(), msg, ErrorSeverity.ERROR)
        }
        FILE_URI_WARNING.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError("/$path", l.toInt(), c.toInt(), msg, ErrorSeverity.WARNING)
        }

        // Kotlin CLI wrapped in [ERROR]: e: /path:line:col: error: msg
        MAVEN_KOTLIN_CLI_ERROR.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError(path, l.toInt(), c.toInt(), msg, ErrorSeverity.ERROR)
        }

        // Kotlin legacy paren format: /path: (line, col) msg
        MAVEN_PAREN_ERROR.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError(path, l.toInt(), c.toInt(), msg, ErrorSeverity.ERROR)
        }
        MAVEN_PAREN_WARNING.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError(path, l.toInt(), c.toInt(), msg, ErrorSeverity.WARNING)
        }

        // Java compiler bracket format: /path:[line,col] msg
        MAVEN_BRACKET_LC_ERROR.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError(path, l.toInt(), c.toInt(), msg, ErrorSeverity.ERROR)
        }
        MAVEN_BRACKET_LC_WARNING.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError(path, l.toInt(), c.toInt(), msg, ErrorSeverity.WARNING)
        }

        // Java compiler bracket, line only: /path:[line] msg
        MAVEN_BRACKET_L_ERROR.find(line)?.let { m ->
            val (path, l, msg) = m.destructured
            return ParsedError(path, l.toInt(), 1, msg, ErrorSeverity.ERROR)
        }
        MAVEN_BRACKET_L_WARNING.find(line)?.let { m ->
            val (path, l, msg) = m.destructured
            return ParsedError(path, l.toInt(), 1, msg, ErrorSeverity.WARNING)
        }

        // Standalone e:/w: formats (not wrapped in [ERROR])
        KOTLIN_E_FILE_URI.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError("/$path", l.toInt(), c.toInt(), msg, ErrorSeverity.ERROR)
        }
        KOTLIN_W_FILE_URI.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError("/$path", l.toInt(), c.toInt(), msg, ErrorSeverity.WARNING)
        }
        KOTLIN_E_COLON.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError(path, l.toInt(), c.toInt(), msg, ErrorSeverity.ERROR)
        }
        KOTLIN_W_COLON.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError(path, l.toInt(), c.toInt(), msg, ErrorSeverity.WARNING)
        }
        KOTLIN_E_PAREN.find(line)?.let { m ->
            val (path, l, c, msg) = m.destructured
            return ParsedError(path, l.toInt(), c.toInt(), msg, ErrorSeverity.ERROR)
        }

        return null
    }

    private fun tryMatchSpotlessError(line: String, filePathMap: Map<String, String>): ParsedError? {
        SPOTLESS_ERROR.find(line)?.let { m ->
            val fileName = m.groupValues[1]
            val lineNum = m.groupValues[2].toInt()
            val message = m.groupValues[3]
            val fullPath = filePathMap[fileName] ?: return null
            return ParsedError(fullPath, lineNum, 1, message, ErrorSeverity.ERROR)
        }
        return null
    }
}
