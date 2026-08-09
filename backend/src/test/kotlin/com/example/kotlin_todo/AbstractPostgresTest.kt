package com.example.kotlin_todo

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * PostgreSQL Testcontainerを全テストで共有する基底クラス。
 *
 * companion objectのvalはJVM内で一度だけ初期化されるため、
 * 複数テストクラスから継承してもPostgreSQLコンテナは1個。
 * コンテナ起動コストを全テスト間でamortize出来る。
 *
 * @ServiceConnectionにより、SpringBootがこのコンテナの接続情報を
 * 自動的にDataSourceプロパティに注入する(Boot 3.1+の機能)。
 * application.propertiesのDataSource URLはTestcontainer側で上書きされる。
 */
@Testcontainers
abstract class AbstractPostgresTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:17"))
                .withDatabaseName("kotlin_todo")
                .withUsername("kotlin_todo")
                .withPassword("kotlin_todo")
    }
}