package com.genkihashioka.kotlintodo.ui.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * TodoDetailViewModelのStateFlowとTodoDetailScreenを接続するComposable。
 */
@Composable
fun TodoDetailRoute(
    viewModel: TodoDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // StateFlowから状態を取得
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TodoDetailScreen(
        uiState = uiState,
        onRetry = { viewModel.retry() },
        onBack = onBack,
        modifier = modifier,
    )
}
