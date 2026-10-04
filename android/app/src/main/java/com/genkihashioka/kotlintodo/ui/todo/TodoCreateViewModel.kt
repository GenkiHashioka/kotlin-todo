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
import kotlin.coroutines.cancellation.CancellationException

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
        // Todo作成途中の場合は、以降の処理を行わない。
        // 画面遷移が完了するまでに再度createTodoが呼ばれると、登録を受け付けてしまうため、isCreatedがtrueの間も処理を受け付けない
        if (_uiState.value.isSubmitting || _uiState.value.isCreated) {
            return
        }

        // 登録開始前にエラー状態をリセットし、送信中フラグをtrueにする
        _uiState.update { currentState ->
            currentState.copy(
                isSubmitting = true,
                hasSubmitError = false,
            )
        }

        val currentState = _uiState.value
        viewModelScope.launch {
            try {
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

                _uiState.update { currentState ->
                    currentState.copy(
                        isCreated = true,
                    )
                }
            } catch (cancellationException: CancellationException) {
                // CoroutineのキャンセルはErrorへ変換せず、呼び出し元へ伝播させる
                throw cancellationException
            } catch (exception: Exception) {
                // その他のExceptionはエラーとして扱う
                _uiState.update { currentState ->
                    currentState.copy(
                        hasSubmitError = true,
                    )
                }
            } finally {
                // 送信中フラグをfalseに戻す。
                _uiState.update { currentState ->
                    currentState.copy(
                        isSubmitting = false,
                    )
                }
            }
        }
    }
}
