package com.genkihashioka.kotlintodo.ui.todo

import com.genkihashioka.kotlintodo.data.remote.model.TodoDto

/**
 * TodoリストのUIの状態を表すデータクラス。
 */
data class TodoListUiState(
    val todos: List<TodoDto> = emptyList(),
)
