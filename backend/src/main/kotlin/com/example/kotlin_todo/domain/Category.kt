package com.example.kotlin_todo.domain

/**
 * カテゴリドメインクラス
 */
data class Category(
    val id: Long,
    val name: String,
    val ownerId: Long,
)
