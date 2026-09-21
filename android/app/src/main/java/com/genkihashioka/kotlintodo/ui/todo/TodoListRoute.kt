package com.genkihashioka.kotlintodo.ui.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * TodoListViewModelのStateFlowとTodoListScreenを接続するComposable。
 * viewModelのuiStateを監視し、現在の状態をScreenに渡す。
 */
@Composable
fun TodoListRoute(
    viewModel: TodoListViewModel,
    modifier: Modifier = Modifier,
) {
    // StateFlowから状態を取得
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TodoListScreen(
        uiState = uiState,
        onRetry = { viewModel.retry() },
        modifier = modifier,
    )
}
