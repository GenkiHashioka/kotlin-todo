package com.genkihashioka.kotlintodo.ui.todo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TodoTitleのテストクラス
 */
class TodoTitleTest {
    @Test
    fun normalizeTodoTitle_withCrLf_replacesWithSingleSpace() {
        // 実行
        val actual = normalizeTodoTitle("Hello\r\nWorld")

        // 検証
        assertEquals("Hello World", actual)
    }

    @Test
    fun normalizeTodoTitle_withLf_replacesWithSpace() {
        // 実行
        val actual = normalizeTodoTitle("Hello\nWorld")

        // 検証
        assertEquals("Hello World", actual)
    }

    @Test
    fun normalizeTodoTitle_withCr_replacesWithSpace() {
        // 実行
        val actual = normalizeTodoTitle("Hello\rWorld")

        // 検証
        assertEquals("Hello World", actual)
    }

    @Test
    fun normalizeTodoTitle_withoutLineBreaks_returnsSameTitle() {
        // 実行
        val actual = normalizeTodoTitle("Hello World")

        // 検証
        assertEquals("Hello World", actual)
    }
}
