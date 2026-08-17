package com.example.kotlin_todo.routes

import com.example.kotlin_todo.domain.Todo
import com.example.kotlin_todo.dto.ErrorResponse
import com.example.kotlin_todo.dto.TodoCreateRequest
import com.example.kotlin_todo.dto.TodoResponse
import com.example.kotlin_todo.dto.TodoUpdateRequest
import com.example.kotlin_todo.dto.toResponse
import com.example.kotlin_todo.service.TodoService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

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
        // 一覧取得
        get {
            val todos = todoService.findAll()
            call.respond(todos.map { todoService.buildResponse(it) })
        }

        // 単体取得
        get("/{id}") {
            // URLからIDを取得。数値でない場合はBadRequestを返却する。
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, INVALID_ID_RESPONSE)

            // 取得処理
            val todo = todoService.findById(id)
            call.respond(todoService.buildResponse(todo))
        }

        // 作成
        post {
            // JSONをDTOに変換
            val request = call.receive<TodoCreateRequest>()

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
        }

        // 更新
        put("/{id}") {
            // URLからIDを取得。数値でない場合はBadRequestを返却する。
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, INVALID_ID_RESPONSE)

            // JSONをDTOに変換
            val request = call.receive<TodoUpdateRequest>()

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
        }

        // 削除
        delete("/{id}") {
            // URLからIDを取得。数値でない場合はBadRequestを返却する。
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, INVALID_ID_RESPONSE)

            // 削除処理
            val deleted = todoService.delete(id)
            call.respond(todoService.buildResponse(deleted))
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