package com.genkihashioka.kotlintodo.ui.todo

import com.genkihashioka.kotlintodo.data.remote.model.TodoDto

/**
 * TodoリストのUIの状態を表すsealed interface。
 */
sealed interface TodoListUiState {
    data object Loading : TodoListUiState

    data class Success(
        val todos: List<TodoDto>,
    ) : TodoListUiState

    data object Empty : TodoListUiState

    data object Error : TodoListUiState
}
