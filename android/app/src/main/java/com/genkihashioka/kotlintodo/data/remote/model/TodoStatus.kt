package com.genkihashioka.kotlintodo.data.remote.model

/**
 * Todoのステータス。
 */
enum class TodoStatus {
    /** 未着手 */
    NOT_STARTED,
    /** 進行中 */
    IN_PROGRESS,
    /** 完了 */
    DONE,
}
