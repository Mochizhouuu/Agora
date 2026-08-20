package com.newoether.agora.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class FileValidatorTest {

    private fun mockContext(mimeType: String?, fileName: String?, fileSize: Long?): Context {
        val context = mock(Context::class.java)
        val contentResolver = mock(ContentResolver::class.java)

        `when`(context.contentResolver).thenReturn(contentResolver)
        `when`(contentResolver.getType(any())).thenReturn(mimeType)

        if (fileName != null || fileSize != null) {
            val cursor = mock(Cursor::class.java)
            `when`(cursor.moveToFirst()).thenReturn(true)
            `when`(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(0)
            `when`(cursor.getString(0)).thenReturn(fileName)
            `when`(cursor.getColumnIndex(OpenableColumns.SIZE)).thenReturn(1)
            if (fileSize != null) {
                `when`(cursor.getLong(1)).thenReturn(fileSize)
            }
            `when`(contentResolver.query(any(), any(), any(), any(), any())).thenReturn(cursor)
        }

        return context
    }

    private fun mockUri(path: String): Uri {
        val uri = mock(Uri::class.java)
        `when`(uri.path).thenReturn(path)
        `when`(uri.lastPathSegment).thenReturn(path.substringAfterLast('/'))
        return uri
    }

    @Test
    fun `validate allows js file with application javascript mime type`() {
        val context = mockContext(
            mimeType = "application/javascript",
            fileName = "app.js",
            fileSize = 1024L
        )
        val uri = mockUri("/media/external/files/app.js")

        val result = FileValidator.validate(context, uri)

        assertTrue(result.valid)
        assertEquals("application/javascript", result.mimeType)
    }

    @Test
    fun `validate allows js file with null mime type via extension fallback`() {
        val context = mockContext(
            mimeType = null,
            fileName = "script.mjs",
            fileSize = 2048L
        )
        val uri = mockUri("/media/external/files/script.mjs")

        val result = FileValidator.validate(context, uri)

        assertTrue(result.valid)
        assertEquals("application/javascript", result.mimeType)
    }

    @Test
    fun `validate allows js file with application octet-stream mime type via extension fallback`() {
        val context = mockContext(
            mimeType = "application/octet-stream",
            fileName = "bundle.cjs",
            fileSize = 4096L
        )
        val uri = mockUri("/media/external/files/bundle.cjs")

        val result = FileValidator.validate(context, uri)

        assertTrue(result.valid)
        assertEquals("application/javascript", result.mimeType)
    }

    @Test
    fun `validate allows python and typescript code files`() {
        val pyContext = mockContext(
            mimeType = "text/x-python",
            fileName = "main.py",
            fileSize = 512L
        )
        val pyUri = mockUri("/media/external/files/main.py")
        val pyResult = FileValidator.validate(pyContext, pyUri)
        assertTrue(pyResult.valid)

        val tsContext = mockContext(
            mimeType = null,
            fileName = "index.ts",
            fileSize = 1024L
        )
        val tsUri = mockUri("/media/external/files/index.ts")
        val tsResult = FileValidator.validate(tsContext, tsUri)
        assertTrue(tsResult.valid)
        assertEquals("application/typescript", tsResult.mimeType)
    }

    @Test
    fun `validate rejects executable binary files`() {
        val context = mockContext(
            mimeType = "application/x-msdownload",
            fileName = "setup.exe",
            fileSize = 1048576L
        )
        val uri = mockUri("/media/external/files/setup.exe")

        val result = FileValidator.validate(context, uri)

        assertFalse(result.valid)
        assertEquals(FileValidator.Error.UNSUPPORTED_TYPE, result.error)
    }

    @Test
    fun `validate rejects oversized non-pdf files`() {
        val context = mockContext(
            mimeType = "application/javascript",
            fileName = "huge.js",
            fileSize = 30L * 1024 * 1024
        )
        val uri = mockUri("/media/external/files/huge.js")

        val result = FileValidator.validate(context, uri)

        assertFalse(result.valid)
        assertEquals(FileValidator.Error.TOO_LARGE, result.error)
    }
}
