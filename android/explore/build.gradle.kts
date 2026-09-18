// :explore -- the exploration policy: frontier, action selection, coverage,
// safety envelope, budgets. This is what makes the scan autonomous.
//
// Owner: Samuel. See ASSIGNMENTS.md.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.agents.anima.explore"
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
    implementation(project(":capture"))
    // ScanController exposes a StateFlow for the scan-control screen (S3).
    // `-core`, not `-android` like :capture/:app use: :explore stays
    // platform-neutral, and StateFlow/MutableStateFlow don't need
    // Dispatchers.Main or any other Android-only piece, so FakeScanController
    // runs its timer on Dispatchers.Default and stays usable from a plain JVM
    // unit test.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
