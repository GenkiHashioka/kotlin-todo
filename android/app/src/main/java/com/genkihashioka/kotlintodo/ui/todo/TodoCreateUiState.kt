package com.genkihashioka.kotlintodo.ui.todo

import com.genkihashioka.kotlintodo.data.remote.model.Priority
import java.time.LocalDate

/**
 * Todo新規作成画面のUI状態を表すstate
 */
data class TodoCreateUiState(
    val title: String = "",
    val description: String = "",
    val dueDate: LocalDate? = null,
    val priority: Priority = Priority.LOW,
    val isSubmitting: Boolean = false,
)
