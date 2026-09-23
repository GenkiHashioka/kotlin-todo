package com.genkihashioka.kotlintodo.data.remote.api

import com.genkihashioka.kotlintodo.data.remote.model.TodoDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Ktor Todo APIへのアクセス方法を定義するインタフェース。
 */
interface TodoApi {
    @GET("todos")
    suspend fun getTodos(): List<TodoDto>

    @GET("todos/{id}")
    suspend fun getTodo(
        @Path("id") todoId: Long,
    ): TodoDto
}
