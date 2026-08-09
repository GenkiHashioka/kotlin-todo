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
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}