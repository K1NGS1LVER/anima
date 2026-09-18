// :store -- SQLite persistence, pack assembly, diffing across scans and
// .animapack export/import.
//
// Owner: Jacob. See ASSIGNMENTS.md.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.agents.anima.store"
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
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
}
