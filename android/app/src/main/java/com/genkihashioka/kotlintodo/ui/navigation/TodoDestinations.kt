package com.genkihashioka.kotlintodo.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Todo一覧画面。
 * 渡す引数はないため、インスタンスが１つだけのdata objectで表現する。
 */
@Serializable
data object TodoListDestination

/**
 * Todo詳細画面。
 * どのTodoを表示するかを識別するtodoIdが必要なため、data classで表現する。
 */
@Serializable
data class TodoDetailDestination(
    val todoId: Long,
)
