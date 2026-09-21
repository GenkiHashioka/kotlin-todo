package com.genkihashioka.kotlintodo.data.remote.api

import com.genkihashioka.kotlintodo.data.remote.model.TodoDto
import retrofit2.http.GET

/**
 * Ktor Todo APIへのアクセス方法を定義するインタフェース。
 */
interface TodoApi {
    @GET("todos")
    suspend fun getTodos(): List<TodoDto>
}
