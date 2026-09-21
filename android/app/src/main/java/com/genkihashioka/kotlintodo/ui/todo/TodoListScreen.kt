package com.genkihashioka.kotlintodo.ui.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.genkihashioka.kotlintodo.R

/**
 * Todoリストを表示するコンポーネント。
 */
@Composable
fun TodoListScreen(
    uiState: TodoListUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (uiState) {
            TodoListUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is TodoListUiState.Success -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = uiState.todos,
                        key = { todo -> todo.id },
                    ) { todo ->
                        Text(text = todo.title)
                    }
                }
            }

            TodoListUiState.Empty -> {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = stringResource(R.string.todo_list_empty),
                )
            }

            TodoListUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = stringResource(R.string.todo_list_error))

                    Button(onClick = onRetry) {
                        Text(text = stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}
