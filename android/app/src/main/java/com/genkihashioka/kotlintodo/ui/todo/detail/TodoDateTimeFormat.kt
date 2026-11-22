package com.genkihashioka.kotlintodo.ui.todo.detail

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** 表示形式 */
private val todoDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

/**
 * APIから受け取った、作成日時、更新日時を表示形式にフォーマットする。
 * 変換に失敗した場合、nullを返却する。
 */
fun formatTodoDateTime(value: String): String? =
    try {
        LocalDateTime.parse(value).format(todoDateTimeFormatter)
    } catch (dateTimeParseException: DateTimeParseException) {
        null
    }
