package com.genkihashioka.kotlintodo.ui.todo.detail

import com.genkihashioka.kotlintodo.data.remote.model.TodoDto

/**
 * Todo詳細画面のUIの状態を表すsealed interface。
 */
sealed interface TodoDetailUiState {
    data object Loading : TodoDetailUiState

    data class Success(
        val todo: TodoDto,
        val isDeleting: Boolean = false,
        val hasDeleteError: Boolean = false,
        val isDeleted: Boolean = false,
    ) : TodoDetailUiState

    data object Error : TodoDetailUiState
}
