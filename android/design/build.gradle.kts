// :design -- brand and design-token extraction: colors, typography, spacing,
// shape, components, light/dark, tone of voice.
//
// Owner: Jiya. See ASSIGNMENTS.md.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.agents.anima.design"
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
        // Extraction is a measurement, so when a token assertion fails the
        // measured numbers are what you need to see. Without this the printout
        // that explains the failure is swallowed.
        unitTests.all { test ->
            test.testLogging {
                showStandardStreams = true
            }
        }
    }
}

dependencies {
    api(project(":core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
