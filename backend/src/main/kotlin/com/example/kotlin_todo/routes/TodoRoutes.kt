@file:OptIn(ExperimentalKtorApi::class)

package com.example.kotlin_todo.routes

import com.example.kotlin_todo.domain.Todo
import com.example.kotlin_todo.dto.error.ErrorResponse
import com.example.kotlin_todo.dto.TodoCreateRequest
import com.example.kotlin_todo.dto.TodoResponse
import com.example.kotlin_todo.dto.TodoUpdateRequest
import com.example.kotlin_todo.dto.toResponse
import com.example.kotlin_todo.service.TodoService
import com.example.kotlin_todo.validation.validateOrThrow
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

/**
 * パスパラメータのidが数値でない場合に返すレスポンス。
 */
private val INVALID_ID_RESPONSE = ErrorResponse(
    status = HttpStatusCode.BadRequest.value,
    message = "Invalid id",
)

/**
 * TodoのCRUDエンドポイント
 *
 * @param devUserId 認証未実施の為、作成時のownerIdに使う開発用のユーザーのID
 */
fun Route.todoRoutes(todoService: TodoService, devUserId: Long) {
    route("/todos") {
        // 500 は Application.kt の StatusPagesが生成するため routing のコードに
        // 現れず、自動生成では拾えない。ここで明示する。
        // 正常系（200 / 201）の description は意図的に空のままにしている。
        // describe で書くと推論した型や Location ヘッダを手で複製することになるため。
        // 経緯と再検討の条件は #37 を参照。
        describe {
            responses {
                response(HttpStatusCode.InternalServerError.value) {
                    description = "サーバ内部エラー"
                    schema = jsonSchema<ErrorResponse>()
                }
            }
        }

        // 一覧取得
        get {
            val todos = todoService.findAll()
            call.respond(todos.map { todoService.buildResponse(it) })
        }

        // 作成
        post {
            // JSONをDTOに変換
            val request = call.receive<TodoCreateRequest>()

            // 検証。失敗した場合はValidationExceptionをスロー
            request.validateOrThrow()

            // 作成処理
            val created = todoService.create(
                // TODO(#23): 認証実装後は認証情報からownerIdを取得する
                ownerId = devUserId,
                title = request.title,
                description = request.description,
                dueDate = request.dueDate,
                priority = request.priority,
                status = request.status,
                categoryId = request.categoryId,
            )
            call.response.header(HttpHeaders.Location, "/todos/${created.id}")
            call.respond(HttpStatusCode.Created, todoService.buildResponse(created))
        }.describe {
            // postが投げる 404, 400 に関しては、post固有なので親ではなくこの操作に直接 describe を記述する
            // 推論は型までしか決められず required は デフォルトで false になる。
            // call.receive はボディが無ければ例外をスローするため、必須であることを明示する。
            requestBody {
                required = true
                schema = jsonSchema<TodoCreateRequest>()
            }
            responses {
                response(HttpStatusCode.BadRequest.value) {
                    description = "リクエストボディが不正（必須項目の欠落、検証エラー、 JSON の形式誤り）"
                    schema = jsonSchema<ErrorResponse>()
                }
                response(HttpStatusCode.NotFound.value) {
                    description = "categoryId に指定した Category が存在しない"
                    schema = jsonSchema<ErrorResponse>()
                }
            }
        }

        // /todos/{id} ルート
        route("/{id}") {
            describe {
                responses {
                    response(HttpStatusCode.NotFound.value) {
                        description = "指定された Todo が存在しない"
                        schema = jsonSchema<ErrorResponse>()
                    }

                    response(HttpStatusCode.BadRequest.value) {
                        description = "パスパラメータの id が数値でない"
                        schema = jsonSchema<ErrorResponse>()
                    }
                }
            }

            // 単体取得
            get {
                // URLからIDを取得。数値でない場合はBadRequestを返却する。
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, INVALID_ID_RESPONSE)

                // 取得処理
                val todo = todoService.findById(id)
                call.respond(todoService.buildResponse(todo))
            }

            // 更新
            put {
                // URLからIDを取得。数値でない場合はBadRequestを返却する。
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, INVALID_ID_RESPONSE)

                // JSONをDTOに変換
                val request = call.receive<TodoUpdateRequest>()

                // 検証。失敗した場合はValidationExceptionをスロー。
                request.validateOrThrow()

                // 更新処理
                val updated = todoService.update(
                    id = id,
                    title = request.title,
                    description = request.description,
                    dueDate = request.dueDate,
                    priority = request.priority,
                    status = request.status,
                    categoryId = request.categoryId,
                )
                call.respond(todoService.buildResponse(updated))
            }.describe {
                // 推論は型までしか決められず required は デフォルトで false になる。
                // call.receive はボディが無ければ例外をスローするため、必須であることを明示する。
                requestBody {
                    required = true
                    schema = jsonSchema<TodoUpdateRequest>()
                }

                responses {
                    response(HttpStatusCode.BadRequest.value) {
                        description = "id が数値でない、またはリクエストボディの検証に失敗した"
                        schema = jsonSchema<ErrorResponse>()
                    }

                    response(HttpStatusCode.NotFound.value) {
                        description = "指定された Todo、または categoryId に指定した Category が存在しない"
                        schema = jsonSchema<ErrorResponse>()
                    }
                }
            }

            // 削除
            delete {
                // URLからIDを取得。数値でない場合はBadRequestを返却する。
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, INVALID_ID_RESPONSE)

                // 削除処理
                val deleted = todoService.delete(id)
                call.respond(todoService.buildResponse(deleted))
            }
        }
    }
}

/**
 * TodoにCategory情報を付けてレスポンスに変換する。
 * categoryIdがnullでなければCategoryを1件引くため、
 * 一覧取得ではTodoの件数分のSELECTが追加で発行される。（N+1問題 / #3）。
 */
private suspend fun TodoService.buildResponse(todo: Todo): TodoResponse {
    // カテゴリIDを検索条件にカテゴリを取得する。
    val category = todo.categoryId?.let { findCategoryById(it) }
    // TodoResponseをカテゴリを設定して返却。
    return todo.toResponse(category)
}
