package com.genkihashioka.kotlintodo.data.repository

import com.genkihashioka.kotlintodo.data.remote.api.TodoApi
import com.genkihashioka.kotlintodo.data.remote.model.TodoDto

/**
 * TodoのAPIを扱うリポジトリクラス。
 */
class TodoRepository(
    private val todoApi: TodoApi,
) {
    /**
     * Todoのリストを全件取得する。
     */
    suspend fun getTodos(): List<TodoDto> {
        return todoApi.getTodos()
    }
}
