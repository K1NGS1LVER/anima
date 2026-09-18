// Root build file for the Anima Android app.
// Plugin versions are declared here once and applied per module.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
}

// Every Android module shares one SDK contract, declared here rather than
// copied into seven build files where it would drift.
extra["animaCompileSdk"] = 34
extra["animaMinSdk"] = 30
extra["animaTargetSdk"] = 34
