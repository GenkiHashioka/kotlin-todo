package com.genkihashioka.kotlintodo.ui.todo

import com.genkihashioka.kotlintodo.data.remote.model.TodoDto

/**
 * Todo詳細画面のUIの状態を表すsealed interface。
 */
sealed interface TodoDetailUiState {
    data object Loading : TodoDetailUiState

    data class Success(
        val todo: TodoDto,
    ) : TodoDetailUiState

    data object Error : TodoDetailUiState
}
