package com.genkihashioka.kotlintodo.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.genkihashioka.kotlintodo.R

/**
 * Todo詳細を表示するコンポーネント。
 */
@Composable
fun TodoDetailScreen(
    uiState: TodoDetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Button(
            onClick = onBack,
        ) {
            Text(text = stringResource(R.string.back))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (uiState) {
                TodoDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is TodoDetailUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val todo = uiState.todo
                        val unsetText = stringResource(R.string.unset)

                        Text(
                            text = stringResource(
                                R.string.todo_detail_title,
                                todo.title,
                            ),
                        )
                        Text(
                            text = stringResource(
                                R.string.todo_detail_description,
                                todo.description ?: unsetText,
                            ),
                        )
                        Text(
                            text = stringResource(
                                R.string.todo_detail_due_date,
                                todo.dueDate ?: unsetText,
                            ),
                        )
                        Text(
                            text = stringResource(
                                R.string.todo_detail_priority,
                                todo.priority.name,
                            ),
                        )
                        Text(
                            text = stringResource(
                                R.string.todo_detail_status,
                                todo.status.name,
                            ),
                        )
                        Text(
                            text = stringResource(
                                R.string.todo_detail_category,
                                todo.category?.name ?: unsetText,
                            ),
                        )
                        Text(
                            text = stringResource(
                                R.string.todo_detail_created_at,
                                todo.createdAt,
                            ),
                        )
                        Text(
                            text = stringResource(
                                R.string.todo_detail_updated_at,
                                todo.updatedAt,
                            ),
                        )
                    }
                }

                TodoDetailUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = stringResource(R.string.todo_detail_error))

                        Button(onClick = onRetry) {
                            Text(text = stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}
