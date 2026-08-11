package com.example.kotlin_todo.domain

import java.time.LocalDateTime

/**
 * ユーザードメインクラス
 */
data class User(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val createdAt: LocalDateTime,
)
