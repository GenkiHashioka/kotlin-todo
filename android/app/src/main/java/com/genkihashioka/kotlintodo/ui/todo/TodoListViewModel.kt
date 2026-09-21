package com.genkihashioka.kotlintodo.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genkihashioka.kotlintodo.data.repository.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Todoリスト表示画面のViewModel。
 *
 * @property todoRepository TodoのAPIを扱うリポジトリ
 */
class TodoListViewModel(
    private val todoRepository: TodoRepository,
) : ViewModel() {
    // UIの状態を表すStateFlow。画面表示時はLoading
    private val _uiState = MutableStateFlow<TodoListUiState>(TodoListUiState.Loading)
    val uiState: StateFlow<TodoListUiState> = _uiState.asStateFlow()

    init {
        loadTodos()
    }

    /**
     * 再試行処理。
     */
    fun retry() {
        loadTodos()
    }

    /**
     * Todoリストの一覧を取得し、画面情報を更新する。
     */
    private fun loadTodos() {
        viewModelScope.launch {
            // 取得開始
            _uiState.value = TodoListUiState.Loading

            try {
                val todos = todoRepository.getTodos()

                // todoが空の場合はEmpty
                if (todos.isEmpty()) {
                    _uiState.value = TodoListUiState.Empty
                } else {
                    _uiState.value = TodoListUiState.Success(todos)
                }
            } catch (cancellationException: CancellationException) {
                // CoroutineのキャンセルはErrorへ変換せず、呼び出し元へ伝播させる
                throw cancellationException
            } catch (exception: Exception) {
                // その他のExceptionはエラーとして扱う
                _uiState.value = TodoListUiState.Error
            }
        }
    }
}
