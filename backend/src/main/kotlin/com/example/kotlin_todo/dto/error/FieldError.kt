package com.example.kotlin_todo.dto.error

import kotlinx.serialization.Serializable

/**
 * フィールドに対するエラー内容詳細。
 */
@Serializable
data class FieldError(
    val field: String,
    val message: String,
)
