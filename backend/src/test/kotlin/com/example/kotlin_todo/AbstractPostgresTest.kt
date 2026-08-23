package com.example.kotlin_todo

import com.example.kotlin_todo.db.DatabaseFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 全Repositoryテスト共通の基底クラス。
 * companion objectのvalは、JVMで一度だけ初期化されるため、
 * PostgreSQLコンテナは全テスト間で、1個共有される。
 */
abstract class AbstractPostgresTest {
    companion object {
        // クラスロード時に1度だけTestcontainerを起動
        private val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:17"))
                .withDatabaseName("kotlin_todo")
                .withUsername("kotlin_todo")
                .withPassword("kotlin_todo")
                .apply { start() }

        // クラスロード時にDatabaseFactoryをTestcontainer経由で初期化
        init {
            val dataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                driverClassName = postgres.driverClassName
                maximumPoolSize = 5
            })
            DatabaseFactory.init(dataSource)
        }
    }
}
