package com.genkihashioka.kotlintodo.data.repository

import com.genkihashioka.kotlintodo.data.remote.api.TodoApi
import com.genkihashioka.kotlintodo.data.remote.model.TodoCreateRequest
import com.genkihashioka.kotlintodo.data.remote.model.TodoDto
import com.genkihashioka.kotlintodo.data.remote.model.TodoUpdateRequest

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

    /**
     * `todoId`に紐づくTodoを取得する。
     */
    suspend fun getTodo(todoId: Long): TodoDto {
        return todoApi.getTodo(todoId)
    }

    /**
     * Todoを新規作成する。
     */
    suspend fun createTodo(todoCreateRequest: TodoCreateRequest): TodoDto {
        return todoApi.createTodo(todoCreateRequest)
    }

    /**
     * 既存のTodoの内容を更新する。
     */
    suspend fun updateTodo(todoId: Long, todoUpdateRequest: TodoUpdateRequest): TodoDto {
        return todoApi.updateTodo(
            todoId = todoId,
            todoUpdateRequest = todoUpdateRequest,
        )
    }

    /**
     * Todoを削除する。
     */
    suspend fun deleteTodo(todoId: Long): TodoDto {
        return todoApi.deleteTodo(todoId)
    }
}
