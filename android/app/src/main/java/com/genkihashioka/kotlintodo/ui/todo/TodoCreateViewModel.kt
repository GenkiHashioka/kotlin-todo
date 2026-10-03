package com.genkihashioka.kotlintodo.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genkihashioka.kotlintodo.data.remote.model.Priority
import com.genkihashioka.kotlintodo.data.remote.model.TodoCreateRequest
import com.genkihashioka.kotlintodo.data.remote.model.TodoStatus
import com.genkihashioka.kotlintodo.data.repository.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Todo新規作成画面のViewModel。
 */
class TodoCreateViewModel(
    private val todoRepository: TodoRepository,
) : ViewModel() {
    // UIの状態を表すStateFlow。
    private val _uiState = MutableStateFlow(TodoCreateUiState())
    val uiState: StateFlow<TodoCreateUiState> = _uiState.asStateFlow()

    /**
     * タイトルの更新。
     */
    fun updateTitle(newTitle: String) {
        _uiState.update { currentState ->
            currentState.copy(title = newTitle)
        }
    }

    /**
     * 説明欄の更新。
     */
    fun updateDescription(newDescription: String) {
        _uiState.update { currentState ->
            currentState.copy(description = newDescription)
        }
    }

    /**
     * 期日の更新。
     */
    fun updateDueDate(newDueDate: LocalDate?) {
        _uiState.update { currentState ->
            currentState.copy(dueDate = newDueDate)
        }
    }

    /**
     * 優先度の更新。
     */
    fun updatePriority(newPriority: Priority) {
        _uiState.update { currentState ->
            currentState.copy(priority = newPriority)
        }
    }

    /**
     * 入力されたTodoの登録。
     */
    fun createTodo() {
        val currentState = _uiState.value
        viewModelScope.launch {
            todoRepository.createTodo(
                TodoCreateRequest(
                    title = currentState.title,
                    description = currentState.description.ifBlank {
                        null
                    },
                    dueDate = currentState.dueDate?.toString(),
                    priority = currentState.priority,
                    status = TodoStatus.NOT_STARTED,
                )
            )
        }
    }
}
