// :capture -- the perception and execution plane. Accessibility service,
// screenshots, scroll, window enumeration.
//
// Owner: Samuel. See ASSIGNMENTS.md.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.agents.anima.capture"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
