package com.genkihashioka.kotlintodo.ui.todo.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Todo詳細画面の日時表示のテストクラス
 */
class TodoDateTimeFormatTest {
    @Test
    fun formatTodoDateTime_withMicroseconds_returnsMinutePrecisionText() {
        // 実行
        val actual = formatTodoDateTime("2026-11-16T00:24:49.721526")

        // 検証
        assertEquals("2026/11/16 00:24", actual)
    }

    @Test
    fun formatTodoDateTime_withMilliseconds_returnsMinutePrecisionText() {
        // 実行
        val actual = formatTodoDateTime("2026-11-16T00:24:49.721")

        // 検証
        assertEquals("2026/11/16 00:24", actual)
    }

    @Test
    fun formatTodoDateTime_withoutSeconds_returnsMinutePrecisionText() {
        // 実行
        val actual = formatTodoDateTime("2026-11-16T00:24")

        // 検証
        assertEquals("2026/11/16 00:24", actual)
    }

    @Test
    fun formatTodoDateTime_withLastSecondOfMinute_truncatesSeconds() {
        // 実行
        val actual = formatTodoDateTime("2026-11-16T23:59:59.999999")

        // 検証
        assertEquals("2026/11/16 23:59", actual)
    }

    @Test
    fun formatTodoDateTime_withSingleDigitFields_padsWithZero() {
        // 実行
        val actual = formatTodoDateTime("2026-01-05T09:03:00.1")

        // 検証
        assertEquals("2026/01/05 09:03", actual)
    }

    @Test
    fun formatTodoDateTime_withInvalidText_returnsNull() {
        // 実行
        val actual = formatTodoDateTime("invalid")

        // 検証
        assertNull(actual)
    }

    @Test
    fun formatTodoDateTime_withEmptyText_returnsNull() {
        // 実行
        val actual = formatTodoDateTime("")

        // 検証
        assertNull(actual)
    }
}
