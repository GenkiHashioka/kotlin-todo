plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("io.ktor.plugin") version "3.5.2"
    application
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// OpenApi 仕様をルーティングのコードから生成する（Ktor 3.3.0 以降の実験的機能）
ktor {
    openApi {
        enabled = true
    }
}

application {
    mainClass.set("com.example.kotlin_todo.ApplicationKt")
}

dependencies {
    implementation(platform("io.ktor:ktor-bom:3.5.2"))
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("io.konform:konform-jvm:0.11.1")
    // OpenAPI 仕様の生成と Swagger UI
    implementation("io.ktor:ktor-server-routing-openapi")
    implementation("io.ktor:ktor-server-swagger")

    // Exposed(Kotlin nativeなSQL DSL)
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.61.0")

    // 接続プール
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Flyway (schema migration)
    implementation("org.flywaydb:flyway-core:11.1.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.1.0")

    // PostgreSQL JDBC driver
    runtimeOnly("org.postgresql:postgresql:42.7.4")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// テスト関連のtask設定
tasks.test {
    useJUnitPlatform()
    // Docker Engine 29 は APIバージョン 1.40未満のクライアントを拒否するようになった。
    // TestContainers は api.version が未設定だと現状 1.32 を既定値として使用するため明示的にバージョンを指定する。(#25)
    systemProperty("api.version", "1.44")
}
