import com.android.build.api.variant.BuildConfigField

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val apiBaseUrlProvider = providers
    .gradleProperty("kotlinTodoApiBaseUrl")
    .orElse("")
    .map { apiBaseUrl ->
        require(apiBaseUrl.isNotBlank()) {
            "kotlinTodoApiBaseUrl is not set. Add it to Gradle User Home's gradle.properties."
        }
        require(apiBaseUrl.endsWith("/")) {
            "kotlinTodoApiBaseUrl must end with '/'."
        }
        apiBaseUrl
    }

android {
    namespace = "com.genkihashioka.kotlintodo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.genkihashioka.kotlintodo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        requireNotNull(variant.buildConfigFields) {
            "BuildConfig generation must be enabled."
        }.put(
            "API_BASE_URL",
            apiBaseUrlProvider.map { apiBaseUrl ->
                BuildConfigField(
                    type = "String",
                    value = "\"$apiBaseUrl\"",
                    comment = "Base URL for the Todo API",
                )
            },
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
