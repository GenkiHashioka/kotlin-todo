package com.genkihashioka.kotlintodo.data.remote.model

import kotlinx.serialization.Serializable

/**
 * カテゴリのDTOクラス。
 *
 * @property id カテゴリのID
 * @property name カテゴリの名前
 */
@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
)
