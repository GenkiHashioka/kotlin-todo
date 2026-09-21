package com.genkihashioka.kotlintodo.data.remote

import com.genkihashioka.kotlintodo.data.remote.api.TodoApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Retrofitを構築し、APIの実装を提供するオブジェクト。
 */
object ApiClient {
    // Jsonを生成する。
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private const val BASE_URL = "http://172.28.55.179:8080/"
    private val contentType = "application/json".toMediaType()

    // Retrofitのインスタンスを生成
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()

    // TodoApiインタフェースの実装オブジェクトを取得し提供
    val todoApi: TodoApi = retrofit.create(TodoApi::class.java)
}
