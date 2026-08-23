@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)

package com.example.kotlin_todo.dto

import com.example.kotlin_todo.domain.Category
import com.example.kotlin_todo.domain.Priority
import com.example.kotlin_todo.domain.Todo
import com.example.kotlin_todo.domain.TodoStatus
import com.example.kotlin_todo.dto.serializer.LocalDateSerializer
import com.example.kotlin_todo.dto.serializer.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * TodoAPIのレスポンスボディ
 * ownerIdは認証情報から決まる値の為公開しない。
 * categoryIdは生の外部キーではなく、categoryとして入れ子で返す。
 */
@Serializable
data class TodoResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val dueDate: LocalDate?,
    val priority: Priority,
    val status: TodoStatus,
    val category: CategorySummary?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/**
 * ドメインのTodoをレスポンスに変換する。
 * Categoryは呼び出し側で取得して渡す。
 */
fun Todo.toResponse(category: Category?): TodoResponse = TodoResponse(
    id = this.id,
    title = this.title,
    description = this.description,
    dueDate = this.dueDate,
    priority = this.priority,
    status = this.status,
    category = category?.toSummary(),
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
)
