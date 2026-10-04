package com.genkihashioka.kotlintodo.ui.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * TodoCreateViewModelのStateFlowとTodoCreateScreenを接続するComposable。
 */
@Composable
fun TodoCreateRoute(
    viewModel: TodoCreateViewModel,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // StateFlowから状態を取得
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isCreated) {
        if (uiState.isCreated) {
            onCreated()
        }
    }

    TodoCreateScreen(
        uiState = uiState,
        onTitleChange = viewModel::updateTitle,
        onDescriptionChange = viewModel::updateDescription,
        onPriorityChange = viewModel::updatePriority,
        onDueDateChange = viewModel::updateDueDate,
        onSubmit = viewModel::createTodo,
        modifier = modifier,
    )
}
