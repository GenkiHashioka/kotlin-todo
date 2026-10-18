package com.genkihashioka.kotlintodo.ui.todo.edit

import com.genkihashioka.kotlintodo.data.remote.model.Priority
import com.genkihashioka.kotlintodo.data.remote.model.TodoStatus
import java.time.LocalDate

/**
 * Todo更新画面のUIの状態を表すsealed interface。
 */
sealed interface TodoEditUiState {
    data object Loading : TodoEditUiState

    data object LoadError : TodoEditUiState

    data class Editing(
        val title: String,
        val description: String,
        val dueDate: LocalDate?,
        val priority: Priority,
        val status: TodoStatus,
        val categoryId: Long?,
        val isSubmitting: Boolean = false,
        val hasSubmitError: Boolean = false,
        val isUpdated: Boolean = false,
    ) : TodoEditUiState
}
