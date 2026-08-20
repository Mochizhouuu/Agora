package com.newoether.agora.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileValidator {
    enum class Error {
        UNKNOWN_TYPE,
        UNSUPPORTED_TYPE,
        TOO_LARGE
    }

    private val MIME_PREFIX_WHITELIST = setOf(
        "text/",
    )

    private val MIME_EXACT_WHITELIST = setOf(
        // Common structured data & documents
        "application/json",
        "application/xml",
        "application/yaml",
        "application/x-yaml",
        "application/pdf",
        "application/rtf",
        "application/toml",
        "application/wasm",
        "application/xhtml+xml",
        "application/graphql",

        // JavaScript & Web
        "application/javascript",
        "application/x-javascript",
        "application/ecmascript",
        "text/javascript",
        "text/x-javascript",
        "text/ecmascript",

        // TypeScript & Web Component
        "application/typescript",
        "application/x-typescript",
        "text/typescript",
        "text/x-typescript",
        "text/jsx",
        "text/tsx",

        // Code / Scripts / Shell
        "application/x-sh",
        "application/x-shellscript",
        "application/x-python",
        "text/x-python",
        "application/x-php",
        "application/x-httpd-php",
        "application/sql",
        "application/x-sql",
        "application/x-c",
        "application/x-cpp",
        "application/x-ruby",
        "application/x-perl",
    )

    private val ALLOWED_EXTENSIONS = setOf(
        // JavaScript / TypeScript / Web
        "js", "mjs", "cjs", "jsx", "ts", "mts", "cts", "tsx", "html", "htm", "css", "scss", "less", "vue", "svelte", "astro",

        // Python / Ruby / Perl / PHP
        "py", "pyw", "rb", "pl", "pm", "php",

        // Java / Kotlin / JVM
        "java", "kt", "kts", "groovy", "scala", "clj",

        // C / C++ / C# / Objective-C
        "c", "cpp", "cc", "cxx", "h", "hpp", "hxx", "cs", "m", "mm",

        // Systems / Shell / Other Code
        "rs", "go", "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd", "swift", "dart", "lua", "r", "asm", "s", "sql", "wasm", "zig", "nim",

        // Data / Config / Markup
        "json", "jsonl", "ndjson", "xml", "yaml", "yml", "toml", "ini", "conf", "config", "properties", "env", "gradle", "md", "markdown", "txt", "log", "csv", "tsv", "svg", "rtf", "tex"
    )

    private const val MAX_SIZE = 20L * 1024 * 1024

    data class Result(val valid: Boolean, val error: Error? = null, val mimeType: String? = null)

    fun validate(context: Context, uri: Uri): Result {
        val rawMimeType = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) { null }

        val fileName = resolveFileName(context, uri) ?: uri.path?.substringAfterLast('/')
        val extension = fileName?.substringAfterLast('.', "")?.lowercase()

        val isMimeAllowed = rawMimeType != null && (
            MIME_PREFIX_WHITELIST.any { rawMimeType.startsWith(it) } ||
            rawMimeType in MIME_EXACT_WHITELIST
        )

        val isExtensionAllowed = !extension.isNullOrEmpty() && extension in ALLOWED_EXTENSIONS

        if (!isMimeAllowed && !isExtensionAllowed) {
            return if (rawMimeType == null && extension.isNullOrEmpty()) {
                Result(false, Error.UNKNOWN_TYPE, null)
            } else {
                Result(false, Error.UNSUPPORTED_TYPE, rawMimeType ?: extension)
            }
        }

        val effectiveMimeType = when {
            isMimeAllowed && rawMimeType != null && rawMimeType != "application/octet-stream" -> rawMimeType
            extension == "js" || extension == "mjs" || extension == "cjs" -> "application/javascript"
            extension == "ts" || extension == "mts" || extension == "cts" -> "application/typescript"
            extension == "jsx" -> "text/jsx"
            extension == "tsx" -> "text/tsx"
            extension == "json" || extension == "jsonl" -> "application/json"
            extension == "xml" -> "application/xml"
            extension == "yaml" || extension == "yml" -> "application/yaml"
            extension == "html" || extension == "htm" -> "text/html"
            extension == "css" -> "text/css"
            extension == "csv" -> "text/csv"
            extension == "md" || extension == "markdown" -> "text/markdown"
            rawMimeType != null && rawMimeType != "application/octet-stream" -> rawMimeType
            else -> "text/plain"
        }

        val fileSize = resolveFileSize(context, uri)

        if (fileSize != null && fileSize > MAX_SIZE && effectiveMimeType != "application/pdf") {
            return Result(false, Error.TOO_LARGE, effectiveMimeType)
        }

        return Result(true, mimeType = effectiveMimeType)
    }

    fun resolveMimeType(context: Context, uriString: String): String? {
        return try {
            context.contentResolver.getType(Uri.parse(uriString))
        } catch (_: Exception) { null }
    }

    fun resolveFileName(context: Context, uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    fun resolveFileSize(context: Context, uri: Uri): Long? {
        return try {
            val cursor = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.SIZE), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) it.getLong(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    fun errorMessage(context: Context, error: Error, mimeType: String? = null): String {
        return when (error) {
            Error.UNKNOWN_TYPE -> context.getString(com.newoether.agora.R.string.file_unknown_type)
            Error.UNSUPPORTED_TYPE -> context.getString(com.newoether.agora.R.string.file_unsupported_type, mimeType ?: "?")
            Error.TOO_LARGE -> context.getString(com.newoether.agora.R.string.file_too_large)
        }
    }
}
