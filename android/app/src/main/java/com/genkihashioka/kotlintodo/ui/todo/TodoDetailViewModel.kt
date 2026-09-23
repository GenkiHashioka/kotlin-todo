package com.genkihashioka.kotlintodo.ui.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.genkihashioka.kotlintodo.data.repository.TodoRepository
import com.genkihashioka.kotlintodo.ui.navigation.TodoDetailDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Todo詳細画面のViewModel。
 *
 * @property todoRepository TodoのAPIを扱うリポジトリ
 * @param savedStateHandle ViewModelに紐づく状態保存領域。Navigationと連携している場合、Destinationへ渡した引数もここから取得できる。
 */
class TodoDetailViewModel(
    private val todoRepository: TodoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // SavedStateHandleからtodoIdを取得する。
    private val todoId = savedStateHandle
        .toRoute<TodoDetailDestination>()
        .todoId

    // UIの状態を表すStateFlow。画面表示時はLoading
    private val _uiState = MutableStateFlow<TodoDetailUiState>(TodoDetailUiState.Loading)
    val uiState: StateFlow<TodoDetailUiState> = _uiState.asStateFlow()

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
     * todoIdに紐づくTodoを取得し、画面情報を更新する。
     */
    private fun loadTodo() {
        viewModelScope.launch {
            // 取得開始
            _uiState.value = TodoDetailUiState.Loading

            try {
                val todo = todoRepository.getTodo(todoId)

                _uiState.value = TodoDetailUiState.Success(todo)
            } catch (cancellationException: CancellationException) {
                // CoroutineのキャンセルはErrorへ変換せず、呼び出し元へ伝播させる
                throw cancellationException
            } catch (exception: Exception) {
                // その他のExceptionはエラーとして扱う
                _uiState.value = TodoDetailUiState.Error
            }
        }
    }
}
