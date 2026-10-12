package com.genkihashioka.kotlintodo.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.getSelectedDate
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.genkihashioka.kotlintodo.R
import com.genkihashioka.kotlintodo.data.remote.model.Priority
import com.genkihashioka.kotlintodo.data.remote.model.TodoStatus
import java.time.LocalDate

/**
 * Todo編集画面を表示するコンポーネント
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditScreen(
    uiState: TodoEditUiState,
    onRetry: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPriorityChange: (Priority) -> Unit,
    onStatusChange: (TodoStatus) -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (uiState) {
                TodoEditUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                TodoEditUiState.LoadError -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = stringResource(R.string.todo_edit_load_error))

                        Button(onClick = onRetry) {
                            Text(text = stringResource(R.string.retry))
                        }
                    }

                }

                is TodoEditUiState.Editing -> {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.title,
                            onValueChange = onTitleChange,
                            enabled = !uiState.isSubmitting && !uiState.isUpdated,
                        )

                        OutlinedTextField(
                            value = uiState.description,
                            onValueChange = onDescriptionChange,
                            enabled = !uiState.isSubmitting && !uiState.isUpdated,
                        )

                        Text(text = stringResource(R.string.priority))
                        Priority.entries.forEach { priority ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.priority == priority,
                                    onClick = { onPriorityChange(priority) },
                                    enabled = !uiState.isSubmitting && !uiState.isUpdated,
                                )
                                Text(
                                    text = priority.name,
                                )
                            }
                        }

                        Text(stringResource(R.string.todo_status))
                        TodoStatus.entries.forEach { todoStatus ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.status == todoStatus,
                                    onClick = { onStatusChange(todoStatus) },
                                    enabled = !uiState.isSubmitting && !uiState.isUpdated,
                                )
                                Text(
                                    text = todoStatus.name,
                                )
                            }
                        }

                        Text(
                            text = if (uiState.dueDate == null) {
                                stringResource(
                                    R.string.todo_edit_due_date,
                                    stringResource(R.string.unset)
                                )
                            } else {
                                stringResource(
                                    R.string.todo_edit_due_date,
                                    uiState.dueDate.toString()
                                )
                            }
                        )
                        var showDatePicker by remember { mutableStateOf(false) }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showDatePicker = true },
                                enabled = !uiState.isSubmitting && !uiState.isUpdated,
                            ) {
                                Text(text = stringResource(R.string.todo_edit_due_date_button))
                            }

                            if (uiState.dueDate != null) {
                                Button(
                                    onClick = { onDueDateChange(null) },
                                    enabled = !uiState.isSubmitting && !uiState.isUpdated,
                                ) {
                                    Text(text = stringResource(R.string.todo_edit_due_date_clear))
                                }
                            }
                        }

                        if (showDatePicker) {
                            // 選択中の日付を管理するためのDatePickerState
                            val datePickerState =
                                rememberDatePickerState(initialSelectedDate = uiState.dueDate)
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            onDueDateChange(datePickerState.getSelectedDate())
                                            showDatePicker = false
                                        },
                                    ) {
                                        Text(text = stringResource(R.string.ok_button))
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showDatePicker = false },
                                    ) {
                                        Text(text = stringResource(R.string.cancel_button))
                                    }
                                },
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }

                        Button(
                            onClick = { onSubmit() },
                            enabled = !uiState.title.isBlank()
                                && !uiState.isSubmitting
                                && !uiState.isUpdated,
                        ) {
                            Text(text = stringResource(R.string.save_button))
                        }
                        if (uiState.hasSubmitError) {
                            Text(text = stringResource(R.string.todo_update_error))
                        }
                    }
                }
            }
        }
    }
}
