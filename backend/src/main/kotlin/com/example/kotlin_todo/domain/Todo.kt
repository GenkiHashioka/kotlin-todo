package com.example.kotlin_todo.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * TODOドメインクラス
 */
data class Todo(
    val id: Long,
    val title: String,
    val description: String?,
    val dueDate: LocalDate?,
    val priority: Priority,
    val status: TodoStatus,
    val categoryId: Long?,
    val ownerId: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
