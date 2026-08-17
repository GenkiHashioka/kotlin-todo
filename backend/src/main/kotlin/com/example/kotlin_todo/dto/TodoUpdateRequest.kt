@file:UseSerializers(LocalDateSerializer::class)

package com.example.kotlin_todo.dto

import com.example.kotlin_todo.domain.Priority
import com.example.kotlin_todo.domain.TodoStatus
import com.example.kotlin_todo.dto.serializer.LocalDateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

/**
 * PUT: /todos/{id} のリクエストボディ。
 * 更新対象のidはURLパスから受け取る。
 */
@Serializable
data class TodoUpdateRequest(
    val title: String,
    val description: String? = null,
    val dueDate: LocalDate? = null,
    val priority: Priority,
    val status: TodoStatus,
    val categoryId: Long? = null,
)