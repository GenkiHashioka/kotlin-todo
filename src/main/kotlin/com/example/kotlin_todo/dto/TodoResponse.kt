package com.example.kotlin_todo.dto

import com.example.kotlin_todo.domain.entity.Category
import com.example.kotlin_todo.domain.entity.Todo
import com.example.kotlin_todo.domain.enums.Priority
import com.example.kotlin_todo.domain.enums.TodoStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * TODO取得時のレスポンスDTO
 */
data class TodoResponse(
    /** ID */
    val id: Long,
    /** タイトル */
    val title: String,
    /** 情報 */
    val description: String?,
    /** 期日 */
    val dueDate: LocalDate?,
    /** 優先度 */
    val priority: Priority,
    /** ステータス */
    val status: TodoStatus,
    /** カテゴリ */
    val category: CategorySummary?,
    /** 作成日 */
    val createdAt: LocalDateTime,
    /** 更新日 */
    val updatedAt: LocalDateTime,
)

/**
 * カテゴリサマリ
 * 必要な情報のみをカテゴリに持たせる
 */
data class CategorySummary(
    /** ID */
    val id: Long,
    /** カテゴリ名 */
    val name: String,
)

/**
 * Todoレスポンスのマッピング関数。
 * Entity→DTOへの変換で使用する。
 */
fun Todo.toResponse(): TodoResponse {
    return TodoResponse(
        id = this.id ?: error("保存前のTodoは変換できません"),
        title = this.title,
        description = this.description,
        dueDate = this.dueDate,
        priority = this.priority,
        status = this.status,
        category = this.category?.toSummary(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
}

/**
 * カテゴリサマリのマッピング関数。
 * Entity→DTOの際、カテゴリから必要な値だけを抽出し、サマリを返却する。
 */
fun Category.toSummary(): CategorySummary {
    return CategorySummary(
        id = this.id ?: error("カテゴリが取得できませんでした。"),
        name = this.name,
    )
}