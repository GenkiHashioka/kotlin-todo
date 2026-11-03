package com.genkihashioka.kotlintodo.ui.todo

import androidx.annotation.StringRes
import com.genkihashioka.kotlintodo.R
import com.genkihashioka.kotlintodo.data.remote.model.Priority
import com.genkihashioka.kotlintodo.data.remote.model.TodoStatus

/**
 * 優先度の表示名の文字列リソースを返却する。
 */
@StringRes
fun Priority.displayNameResourceId(): Int = when (this) {
    Priority.LOW -> R.string.priority_low
    Priority.MEDIUM -> R.string.priority_medium
    Priority.HIGH -> R.string.priority_high
}

/**
 * Todoのステータスの文字列リソースを返却する。
 */
@StringRes
fun TodoStatus.displayNameResourceId(): Int = when (this) {
    TodoStatus.NOT_STARTED -> R.string.todo_status_not_started
    TodoStatus.IN_PROGRESS -> R.string.todo_status_in_progress
    TodoStatus.DONE -> R.string.todo_status_done
}
