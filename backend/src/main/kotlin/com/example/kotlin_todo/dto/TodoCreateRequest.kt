@file:UseSerializers(LocalDateSerializer::class)

package com.example.kotlin_todo.dto

import com.example.kotlin_todo.domain.Priority
import com.example.kotlin_todo.domain.TodoStatus
import com.example.kotlin_todo.dto.serializer.LocalDateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

/**
 * POST: /todos のリクエストボディ。
 * id / createdAt / updatedAt / ownerIdは、サーバーが決める値の為、受け取らない
 */
@Serializable
data class TodoCreateRequest(
    val title: String,
    val description: String? = null,
    val dueDate: LocalDate? = null,
    val priority: Priority,
    val status: TodoStatus,
    val categoryId: Long? = null,
)
