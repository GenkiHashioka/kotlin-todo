package com.example.kotlin_todo.dto

/**
 * エラーレスポンス用のDTO
 */
data class ErrorResponse(
    /** ステータスコード */
    val status: Int,
    /** エラーメッセージ */
    val message: String,
)