package com.genkihashioka.kotlintodo.ui.todo.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import com.genkihashioka.kotlintodo.R
import com.genkihashioka.kotlintodo.data.remote.model.Priority
import java.time.LocalDate

/**
 * Todo作成画面を表示するコンポーネント
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoCreateScreen(
    uiState: TodoCreateUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPriorityChange: (Priority) -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 戻る操作の可否を計算する。
    val canGoBack = !uiState.isSubmitting && !uiState.isCreated

    BackHandler(enabled = !canGoBack) {
        // 送信中・登録済みの戻る操作は処理を行わない。
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = uiState.title,
            label = {
                Text(text = stringResource(R.string.todo_create_title_label))
            },
            onValueChange = onTitleChange,
            enabled = !uiState.isSubmitting && !uiState.isCreated,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.description,
            label = {
                Text(text = stringResource(R.string.todo_create_description_label))
            },
            onValueChange = onDescriptionChange,
            enabled = !uiState.isSubmitting && !uiState.isCreated,
            modifier = Modifier.fillMaxWidth(),
        )
        Priority.entries.forEach { priority ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = uiState.priority == priority,
                    onClick = { onPriorityChange(priority) },
                    enabled = !uiState.isSubmitting && !uiState.isCreated,
                )
                Text(
                    text = priority.name,
                )
            }
        }
        Text(
            text = if (uiState.dueDate == null) {
                stringResource(R.string.todo_create_due_date, stringResource(R.string.unset))
            } else {
                stringResource(R.string.todo_create_due_date, uiState.dueDate.toString())
            }
        )
        var showDatePicker by remember { mutableStateOf(false) }
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { showDatePicker = true },
                enabled = !uiState.isSubmitting && !uiState.isCreated,
            ) {
                Text(text = stringResource(R.string.todo_create_due_date_button))
            }

            if (uiState.dueDate != null) {
                Button(
                    onClick = { onDueDateChange(null) },
                    enabled = !uiState.isSubmitting && !uiState.isCreated,
                ) {
                    Text(text = stringResource(R.string.todo_create_due_date_clear))
                }
            }
        }

        if (showDatePicker) {
            // 選択中の日付を管理するためのDatePickerState
            val datePickerState = rememberDatePickerState(initialSelectedDate = uiState.dueDate)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDueDateChange(datePickerState.getSelectedDate())
                            showDatePicker = false
                        },
                    ) {
                        Text(stringResource(R.string.ok_button))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDatePicker = false },
                    ) {
                        Text(text = stringResource(R.string.cancel_button))
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        Button(
            onClick = { onSubmit() },
            enabled = uiState.title.isNotBlank()
                && !uiState.isSubmitting
                && !uiState.isCreated,
        ) {
            Text(text = stringResource(R.string.submit_button))
        }

        // 送信中表示
        if (uiState.isSubmitting) {
            Text(text = stringResource(R.string.submitting))
        }

        if (uiState.hasSubmitError) {
            Text(text = stringResource(R.string.todo_create_error))
        }
    }
}
