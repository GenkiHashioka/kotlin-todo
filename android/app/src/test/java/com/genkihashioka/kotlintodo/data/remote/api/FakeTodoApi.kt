package com.genkihashioka.kotlintodo.data.remote.api

import com.genkihashioka.kotlintodo.data.remote.model.TodoCreateRequest
import com.genkihashioka.kotlintodo.data.remote.model.TodoDto
import com.genkihashioka.kotlintodo.data.remote.model.TodoUpdateRequest
import kotlinx.coroutines.CompletableDeferred

class FakeTodoApi : TodoApi {
    var todosToReturn: List<TodoDto> = emptyList()
    var exceptionToThrow: Exception? = null
    var pendingTodosResponse: CompletableDeferred<List<TodoDto>>? = null
    var getTodosCallCount: Int = 0
        private set

    override suspend fun getTodos(): List<TodoDto> {
        getTodosCallCount++

        val exception = exceptionToThrow

        if (exception != null) throw exception

        val pendingResponse = pendingTodosResponse

        if (pendingResponse != null) return pendingResponse.await()

        return todosToReturn
    }

    override suspend fun getTodo(todoId: Long): TodoDto {
        error("このテストではgetTodoを使用しません")
    }

    override suspend fun createTodo(todoCreateRequest: TodoCreateRequest): TodoDto {
        error("このテストではcreateTodoを使用しません")
    }

    override suspend fun updateTodo(
        todoId: Long,
        todoUpdateRequest: TodoUpdateRequest
    ): TodoDto {
        error("このテストではupdateTodoを使用しません")
    }

    override suspend fun deleteTodo(todoId: Long): TodoDto {
        error("このテストではdeleteTodoを使用しません")
    }
}
