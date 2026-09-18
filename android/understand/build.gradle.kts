// :understand -- LLM/VLM screen, element and journey semantics behind the
// ScreenUnderstander interface in :core.
//
// Owner: Daniel. See ASSIGNMENTS.md.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.agents.anima.understand"
    compileSdk = rootProject.extra["animaCompileSdk"] as Int

    defaultConfig {
        minSdk = rootProject.extra["animaMinSdk"] as Int
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
