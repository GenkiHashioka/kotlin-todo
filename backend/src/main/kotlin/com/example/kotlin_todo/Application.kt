package com.example.kotlin_todo

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
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages) { }
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "UP"))
        }
    }
}

// このクラスはJSON変換対象
@Serializable
data class HealthResponse(val status: String)