package com.example.kotlin_todo

import com.example.kotlin_todo.db.DatabaseFactory
import com.example.kotlin_todo.dev.DevDataInitializer
import com.example.kotlin_todo.dto.ErrorResponse
import com.example.kotlin_todo.exception.CategoryNotFoundException
import com.example.kotlin_todo.exception.TodoNotFoundException
import com.example.kotlin_todo.repository.CategoryRepository
import com.example.kotlin_todo.repository.TodoRepository
import com.example.kotlin_todo.repository.UserRepository
import com.example.kotlin_todo.routes.todoRoutes
import com.example.kotlin_todo.service.TodoService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
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
        get("/health") {
            call.respond(HealthResponse(status = "UP"))
        }
        todoRoutes(todoService = todoService, devUserId = devUserId)
    }
}

// このクラスはJSON変換対象
@Serializable
data class HealthResponse(val status: String)