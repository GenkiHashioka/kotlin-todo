package com.genkihashioka.kotlintodo.data.remote.model

import kotlinx.serialization.Serializable

/**
 * POST /todosへ送るリクエスト。
 *
 * @property description 未入力ならnull
 * @property dueDate 日付は `yyyy-MM-dd` 形式
 */
@Serializable
data class TodoCreateRequest(
    val title: String,
    val description: String? = null,
    val dueDate: String? = null,
    val priority: Priority,
    val status: TodoStatus,
    val categoryId: Long? = null,
)
