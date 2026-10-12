package com.genkihashioka.kotlintodo.ui.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * TodoEditViewModelのStateFlowとTodoEditScreenを接続するComposable
 */
@Composable
fun TodoEditRoute(
    viewModel: TodoEditViewModel,
    onUpdated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // StateFlowから状態を取得
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 更新完了フラグ
    val isUpdated = (uiState as? TodoEditUiState.Editing)?.isUpdated ?: false

    LaunchedEffect(isUpdated) {
        if (isUpdated) {
            onUpdated()
        }
    }

    TodoEditScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onTitleChange = viewModel::updateTitle,
        onDescriptionChange = viewModel::updateDescription,
        onPriorityChange = viewModel::updatePriority,
        onStatusChange = viewModel::updateStatus,
        onDueDateChange = viewModel::updateDueDate,
        onSubmit = viewModel::updateTodo,
        modifier = modifier,
    )
}
