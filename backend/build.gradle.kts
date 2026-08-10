plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.serialization") version "2.3.21"
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

application {
	mainClass.set("com.example.kotlin_todo.ApplicationKt")
}

dependencies {
	implementation(platform("io.ktor:ktor-bom:3.2.0"))
	implementation("io.ktor:ktor-server-netty")
	implementation("io.ktor:ktor-server-content-negotiation")
	implementation("io.ktor:ktor-serialization-kotlinx-json")
	implementation("io.ktor:ktor-server-call-logging")
	implementation("io.ktor:ktor-server-status-pages")
	implementation("ch.qos.logback:logback-classic:1.5.16")

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
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}