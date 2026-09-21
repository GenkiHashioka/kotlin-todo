package com.genkihashioka.kotlintodo.ui.todo

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Todoリストを表示するコンポーネント。
 */
@Composable
fun TodoListScreen(
    uiState: TodoListUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(
            items = uiState.todos,
            key = { todo -> todo.id },
        ) { todo ->
            Text(text = todo.title)
        }
    }
}
