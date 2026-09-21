package com.genkihashioka.kotlintodo.data.remote.model

import kotlinx.serialization.Serializable

/**
 * Ktor APIから受け取るTodoのDTO。
 *
 * @property id TodoのID。
 * @property title Todoのタイトル。
 * @property description Todoの説明。未設定の場合はnull。
 * @property dueDate Todoの期限。未設定の場合はnull 形式は `yyyy-MM-dd`
 * @property priority Todoの優先度。
 * @property status Todoのステータス。
 * @property category Todoのカテゴリ。 未設定の場合はnull
 * @property createdAt Todoの作成日時。
 * @property updatedAt Todoの更新日時。
 */
@Serializable
data class TodoDto(
    val id: Long,
    val title: String,
    val description: String?,
    val dueDate: String?,
    val priority: Priority,
    val status: TodoStatus,
    val category: CategoryDto?,
    val createdAt: String,
    val updatedAt: String,
)
