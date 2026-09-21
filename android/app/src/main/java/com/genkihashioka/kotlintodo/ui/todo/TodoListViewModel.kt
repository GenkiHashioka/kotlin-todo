package com.genkihashioka.kotlintodo.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genkihashioka.kotlintodo.data.repository.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Todoリスト表示画面のViewModel。
 *
 * @property todoRepository TodoのAPIを扱うリポジトリ
 */
class TodoListViewModel(
    private val todoRepository: TodoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TodoListUiState())
    val uiState: StateFlow<TodoListUiState> = _uiState.asStateFlow()

    init {
        loadTodos()
    }

    /**
     * Todoリストの一覧を取得し、画面情報を更新する。
     */
    private fun loadTodos() {
        viewModelScope.launch {
            val todos = todoRepository.getTodos()

            // _uiStateを取得したtodo一覧で更新
            _uiState.update { currentState ->
                currentState.copy(todos = todos)
            }
        }
    }
}
