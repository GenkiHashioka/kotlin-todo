package com.example.kotlin_todo.dto

import com.example.kotlin_todo.domain.Category
import kotlinx.serialization.Serializable

/**
 * TodoResponseに入れ子で含めるカテゴリの要約情報。
 */
@Serializable
data class CategorySummary(
    val id: Long,
    val name: String,
)

/**
 * ドメインのCategoryを、レスポンス用の要約に変換する。
 */
fun Category.toSummary(): CategorySummary = CategorySummary(
    id = this.id,
    name = this.name,
)
