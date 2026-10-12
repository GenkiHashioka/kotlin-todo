package com.genkihashioka.kotlintodo.ui.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.genkihashioka.kotlintodo.data.remote.model.Priority
import com.genkihashioka.kotlintodo.data.remote.model.TodoStatus
import com.genkihashioka.kotlintodo.data.remote.model.TodoUpdateRequest
import com.genkihashioka.kotlintodo.data.repository.TodoRepository
import com.genkihashioka.kotlintodo.ui.navigation.TodoEditDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Todo更新画面のViewModel。
 *
 * @property todoRepository TodoのAPIを扱うリポジトリ
 * @param savedStateHandle ViewModelに紐づく状態保存領域。Navigationと連携している場合、Destinationへ渡した引数もここから取得できる。
 */
class TodoEditViewModel(
    private val todoRepository: TodoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // SavedStateHandleから編集対象のtodoIdを取得する
    private val todoId = savedStateHandle
        .toRoute<TodoEditDestination>()
        .todoId

    // UIの状態を表すStateFlow。画面表示時はLoading
    private val _uiState = MutableStateFlow<TodoEditUiState>(TodoEditUiState.Loading)
    val uiState: StateFlow<TodoEditUiState> = _uiState.asStateFlow()

    init {
        loadTodo()
    }

    /**
     * 再試行処理。
     */
    fun retry() {
        loadTodo()
    }

    /**
     * タイトルの更新。
     */
    fun updateTitle(newTitle: String) {
        _uiState.update { currentState ->
            if (currentState is TodoEditUiState.Editing) {
                currentState.copy(title = newTitle)
            } else {
                currentState
            }
        }
    }

    /**
     * 説明の更新。
     */
    fun updateDescription(newDescription: String) {
        _uiState.update { currentState ->
            if (currentState is TodoEditUiState.Editing) {
                currentState.copy(description = newDescription)
            } else {
                currentState
            }
        }
    }

    /**
     * 期日の更新。
     */
    fun updateDueDate(newDueDate: LocalDate?) {
        _uiState.update { currentState ->
            if (currentState is TodoEditUiState.Editing) {
                currentState.copy(dueDate = newDueDate)
            } else {
                currentState
            }
        }
    }

    /**
     * 優先度の更新。
     */
    fun updatePriority(newPriority: Priority) {
        _uiState.update { currentState ->
            if (currentState is TodoEditUiState.Editing) {
                currentState.copy(priority = newPriority)
            } else {
                currentState
            }
        }
    }

    /**
     * ステータスの更新。
     */
    fun updateStatus(newStatus: TodoStatus) {
        _uiState.update { currentState ->
            if (currentState is TodoEditUiState.Editing) {
                currentState.copy(status = newStatus)
            } else {
                currentState
            }
        }
    }

    /**
     * Todoの更新
     */
    fun updateTodo() {
        val currentState = _uiState.value
        // 現在のstateがEditing以外なら以降の処理は行わない。
        if (currentState !is TodoEditUiState.Editing) {
            return
        }
        // タイトルが空白のみまたは、Todo送信中、またはTodo更新済みなら以降の処理は行わない。
        if (currentState.title.isBlank() || currentState.isSubmitting || currentState.isUpdated) {
            return
        }

        // 保存開始
        _uiState.update { state ->
            if (state is TodoEditUiState.Editing) {
                state.copy(
                    isSubmitting = true,
                    hasSubmitError = false,
                )
            } else {
                state
            }
        }

        val todoUpdateRequest = TodoUpdateRequest(
            title = currentState.title,
            description = currentState.description.ifBlank { null },
            dueDate = currentState.dueDate?.toString(),
            priority = currentState.priority,
            status = currentState.status,
            categoryId = currentState.categoryId,
        )

        viewModelScope.launch {
            try {
                todoRepository.updateTodo(
                    todoId = todoId,
                    todoUpdateRequest = todoUpdateRequest,
                )

                // 更新完了
                _uiState.update { state ->
                    if (state is TodoEditUiState.Editing) {
                        state.copy(isUpdated = true)
                    } else {
                        state
                    }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                // 保存失敗フラグを立てる。
                _uiState.update { state ->
                    if (state is TodoEditUiState.Editing) {
                        state.copy(hasSubmitError = true)
                    } else {
                        state
                    }
                }
            } finally {
                // 送信中の状態を解除する。
                _uiState.update { state ->
                    if (state is TodoEditUiState.Editing) {
                        state.copy(isSubmitting = false)
                    } else {
                        state
                    }
                }
            }
        }
    }

    /**
     * todoIdに紐づくTodoを取得し、画面表示情報を更新する。
     */
    private fun loadTodo() {
        viewModelScope.launch {
            // 取得開始
            _uiState.value = TodoEditUiState.Loading

            try {
                val todo = todoRepository.getTodo(todoId)

                _uiState.value = TodoEditUiState.Editing(
                    title = todo.title,
                    description = todo.description ?: "",
                    dueDate = todo.dueDate?.let {
                        LocalDate.parse(it)
                    },
                    priority = todo.priority,
                    status = todo.status,
                    categoryId = todo.category?.id,
                )
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                _uiState.value = TodoEditUiState.LoadError
            }
        }
    }
}
