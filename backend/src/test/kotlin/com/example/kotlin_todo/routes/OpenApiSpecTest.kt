package com.example.kotlin_todo.routes

import com.example.kotlin_todo.repository.CategoryRepository
import com.example.kotlin_todo.repository.TodoRepository
import com.example.kotlin_todo.service.TodoService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 生成される OpenAPI 仕様を検証する。
 *
 * DB には接続しない。 TodoService も Repository もコンストラクタでは DB に触れず、
 * suspend fun の中でしかトランザクションを張らないため、素で組み立てられる。
 */
class OpenApiSpecTest {
    /**
     * ルーティングだけを組み立てたアプリを起動し、生成された仕様書を渡す。
     */
    private fun withOpenApiSpec(assertions: (JsonObject) -> Unit) = testApplication {
        routing {
            todoRoutes(
                todoService = TodoService(
                    categoryRepository = CategoryRepository(),
                    todoRepository = TodoRepository(),
                ),
                devUserId = 1L,
            )
            openApiRoutes()
        }

        val body = client.get("/openapi.json").bodyAsText()
        assertions(Json.parseToJsonElement(body).jsonObject)
    }

    /**
     * 指定したパスとメソッドの操作を取り出す。
     */
    private fun JsonObject.operation(path: String, method: String): JsonObject =
        this["paths"]!!.jsonObject[path]!!.jsonObject[method]!!.jsonObject

    /**
     * requestBody やレスポンスが参照している型名を取り出す。
     */
    private fun JsonElement?.schemaRef(): String? =
        this?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("application/json")?.jsonObject
            ?.get("schema")?.jsonObject
            ?.get("\$ref")?.jsonPrimitive?.content

    @Test
    fun `5つのエンドポイントが仕様書に載る`() {
        withOpenApiSpec { spec ->
            val paths = spec["paths"]!!.jsonObject

            // 検証
            assertEquals(setOf("/todos", "/todos/{id}"), paths.keys)
            assertEquals(setOf("get", "post"), paths["/todos"]!!.jsonObject.keys)
            assertEquals(setOf("get", "put", "delete"), paths["/todos/{id}"]!!.jsonObject.keys)
        }
    }

    @Test
    fun `404は親の describe から継承され ErrorResponse を参照する`() {
        withOpenApiSpec { spec ->
            listOf("get", "put", "delete").forEach { method ->
                val responses = spec.operation("/todos/{id}", method)["responses"]!!.jsonObject

                // 検証
                assertEquals(
                    "#/components/schemas/ErrorResponse",
                    responses["404"].schemaRef(),
                    "$method /todos/{id} の 404", // メッセージ。どのメソッドで落ちているのかわかりやすくする。
                )
            }
        }
    }

    @Test
    fun `ErrorResponse と入れ子の FieldError がスキーマに登録される`() {
        withOpenApiSpec { spec ->
            val schemas = spec["components"]!!.jsonObject["schemas"]!!.jsonObject.keys

            // 検証
            assertTrue("ErrorResponse" in schemas, "実際の一覧: $schemas")
            assertTrue("FieldError" in schemas, "実際の一覧: $schemas")
        }
    }

    @Test
    fun `リクエストボディは操作ごとに正しい型を参照する`() {
        withOpenApiSpec { spec ->
            // 検証
            assertEquals(
                "#/components/schemas/TodoCreateRequest",
                spec.operation("/todos", "post")["requestBody"].schemaRef(),
            )

            assertEquals(
                "#/components/schemas/TodoUpdateRequest",
                spec.operation("/todos/{id}", "put")["requestBody"].schemaRef(),
            )
        }
    }
}
