package com.example.kotlin_todo

import com.example.kotlin_todo.db.DatabaseFactory
import com.example.kotlin_todo.dev.DevDataInitializer
import com.example.kotlin_todo.dto.error.ErrorResponse
import com.example.kotlin_todo.dto.error.FieldError
import com.example.kotlin_todo.exception.CategoryNotFoundException
import com.example.kotlin_todo.exception.TodoNotFoundException
import com.example.kotlin_todo.repository.CategoryRepository
import com.example.kotlin_todo.repository.TodoRepository
import com.example.kotlin_todo.repository.UserRepository
import com.example.kotlin_todo.routes.openApiRoutes
import com.example.kotlin_todo.routes.todoRoutes
import com.example.kotlin_todo.service.TodoService
import com.example.kotlin_todo.validation.ValidationException
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable

/**
 * プロジェクトのエントリポイント。
 */
fun main() {
    // Ktorのサーバー起動関数
    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module,
    ).start(wait = true) // main関数を動かし続ける。
}

/**
 * サーバー起動時のセットアップ関数
 */
fun Application.module() {
    // DB初期化 (HikariCP → Flyway → Exposed)。Repositoryを使用する前に必ず実行する。
    DatabaseFactory.init()
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages) {
        // 存在しないTodoを指定された場合は404を返却する。
        exception<TodoNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = cause.message ?: "Todo not found"
                ),
            )
        }
        // 存在しないCategoryを指定された場合は404を返却する。
        exception<CategoryNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = cause.message ?: "Category not found"
                ),
            )
        }

        // リクエストボディの検証に失敗した場合は400BadRequestを返却する。
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Validation failed",
                    fieldErrors = cause.fieldErrors,
                )
            )
        }

        // リクエストボディがDTOに変換できない場合は400BadRequestを返却する。
        exception<BadRequestException> { call, cause ->
            // causeからMissingFieldExceptionを取り出す。
            val missingFields = generateSequence<Throwable>(cause) { it.cause }
                .filterIsInstance<MissingFieldException>()
                .firstOrNull()
                ?.missingFields

            // MissingFieldExceptionが見つかった場合、
            if (missingFields != null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        status = HttpStatusCode.BadRequest.value,
                        message = "Validation failed",
                        fieldErrors = missingFields.map {
                            FieldError(field = it, message = "is required")
                        }
                    ),
                )
            } else {
                // 原因を機械的に特定不能。詳細はログのみに残す。
                call.application.log.warn("Malformed request body", cause)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        status = HttpStatusCode.BadRequest.value,
                        message = "Malformed request body",
                    )
                )
            }
        }

        // 想定外の例外は500を返却する。詳細はログのみに残す。
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Internal server error"
                )
            )
        }
    }

    // 手動DI(ADR 0014) 依存を生成し、上位に注入する。
    val userRepository = UserRepository()
    val categoryRepository = CategoryRepository()
    val todoRepository = TodoRepository()
    val todoService = TodoService(
        categoryRepository = categoryRepository,
        todoRepository = todoRepository,
    )

    // TODO(#23): 認証実装後は削除する。ownerIdに使う開発用のユーザーを用意する
    val devUserId = runBlocking {
        DevDataInitializer.ensureDevUser(userRepository).id
    }

    routing {
        // ヘルスチェック
        get("/health") {
            call.respond(HealthResponse(status = "UP"))
        }

        // Todoエンドポイント
        todoRoutes(todoService = todoService, devUserId = devUserId)

        // OpenAPIエンドポイント
        openApiRoutes()
    }
}

// このクラスはJSON変換対象
@Serializable
data class HealthResponse(val status: String)
