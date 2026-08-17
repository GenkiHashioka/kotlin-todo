package com.example.kotlin_todo.dto

import kotlinx.serialization.Serializable

/**
 * エラー時のレスポンスボディ。
 */
@Serializable
data class ErrorResponse(
    val status: Int,
    val message: String,
)