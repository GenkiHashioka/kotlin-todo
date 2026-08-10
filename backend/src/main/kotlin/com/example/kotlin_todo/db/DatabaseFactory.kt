package com.example.kotlin_todo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

/**
 * DB接続プールを立ち上げ、Flywayでスキーマをマイグレーションし、Exposedに接続を登録する。
 * Application起動時に一度だけ呼ぶ。
 */
object DatabaseFactory {
    // 初期化
    fun init() {
        // 接続プール作成
        val dataSource = createHikariDataSource()

        // Flywayでスキーマを最新状態にそろえる。
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()

        // Exposedに「デフォルト接続として使うDataSource」を登録
        Database.connect(dataSource)
    }
}

/**
 * HikariCPで接続プールを作成する
 */
private fun createHikariDataSource(): HikariDataSource {
    // DB設定
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost:5432/kotlin_todo"
        username = "kotlin_todo"
        password = "kotlin_todo"
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
    }
    return HikariDataSource(config)
}