package com.genkihashioka.kotlintodo.ui.todo.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onEditClick: (Long) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // StateFlowから状態を取得
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 削除完了フラグ
    val isDeleted = (uiState as? TodoDetailUiState.Success)?.isDeleted ?: false

    LaunchedEffect(isDeleted) {
        if (isDeleted) {
            onDeleted()
        }
    }

    TodoDetailScreen(
        uiState = uiState,
        onRetry = { viewModel.retry() },
        onBack = onBack,
        onEditClick = onEditClick,
        onDeleteClick = viewModel::deleteTodo,
        modifier = modifier,
    )
}
