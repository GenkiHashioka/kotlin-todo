@file:OptIn(ExperimentalKtorApi::class)

package com.example.kotlin_todo.routes

import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.routingRoot
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json

/**
 * 仕様書のタイトルとバージョン。JSON と Swagger UI の両方から参照する。
 */
private val API_INFO = OpenApiInfo("kotlin-todo API", "0.1")

/**
 * OpenAPI 仕様の出力専用。値が null のフィールドを省き、人が読める形に整える。
 * アプリ本体のレスポンスに影響させないため、ContentNegotiation とは別に用意する。
 */
private val openApiJson = Json {
    explicitNulls = false
    prettyPrint = true
}

fun Route.openApiRoutes() {
    // OpenAPI仕様の生 JSON。テストと curl から読む。
    // hide() を付与することで、この経路自身が仕様書に載らないようにする
    get("/openapi.json") {
        val doc = OpenApiDoc(info = API_INFO) + call.application.routingRoot.descendants()
        call.respondText(openApiJson.encodeToString(doc), ContentType.Application.Json)
    }.hide()

    // Swagger UI。 ブラウザで見る用。
    swaggerUI("/swagger") {
        info = API_INFO
        source = OpenApiDocSource.Routing()
    }
}
