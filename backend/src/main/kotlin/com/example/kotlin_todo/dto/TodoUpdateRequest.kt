package com.example.kotlin_todo.dto

import com.example.kotlin_todo.domain.entity.Category
import com.example.kotlin_todo.domain.entity.Todo
import com.example.kotlin_todo.domain.enums.Priority
import com.example.kotlin_todo.domain.enums.TodoStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * TODO更新時のリクエストDTO
 */
data class TodoUpdateRequest(
    /** TODOタイトル */
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,
    /** 優先度 */
    @field:NotNull
    val priority: Priority,
    /** ステータス */
    @field:NotNull
    val status: TodoStatus,
    /** 情報 */
    val description: String? = null,
    /** 期日 */
    val dueDate: LocalDate? = null,
    /** カテゴリID */
    val categoryId: Long? = null,
)

/**
 * TODO更新用のマッピング関数。
 * 変更内容をエンティティに反映させる。
 */
fun Todo.applyUpdate(request: TodoUpdateRequest, category: Category?) {
    this.title = request.title
    this.priority = request.priority
    this.status = request.status
    this.description = request.description
    this.dueDate = request.dueDate
    this.category = category
}