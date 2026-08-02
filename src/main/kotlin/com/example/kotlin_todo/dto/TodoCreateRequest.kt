package com.example.kotlin_todo.dto

import com.example.kotlin_todo.domain.entity.Category
import com.example.kotlin_todo.domain.entity.Todo
import com.example.kotlin_todo.domain.entity.User
import com.example.kotlin_todo.domain.enums.Priority
import com.example.kotlin_todo.domain.enums.TodoStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * Todo作成リクエスト用DTO
 */
data class TodoCreateRequest(
    /** Todoのタイトル */
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,
    /** 優先度 */
    @field:NotNull
    val priority: Priority,
    /** Todoの情報 */
    val description: String? = null,
    /** 期日 */
    val dueDate: LocalDate? = null,
    /** カテゴリID */
    val categoryId: Long? = null,
)

/**
 * Todo作成リクエスト用のマッピング関数。
 * ownerとcategoryを受け取りDTO→Entityに変換する。
 */
fun TodoCreateRequest.toEntity(owner: User, category: Category?): Todo {
    return Todo(
        title = this.title,
        priority = this.priority,
        description = this.description,
        dueDate = this.dueDate,
        // service層で取得し、引数として受け取る。
        category = category,
        owner = owner,
        // デフォルト値を指定する。
        status = TodoStatus.NOT_STARTED,
    )
}
