// :core -- the contract. Pure Kotlin/JVM on purpose: no `android.*` import is
// allowed in here, which is what lets the whole engine be unit-tested on a
// laptop with no emulator and no device.
//
// Owner: Jacob (pack schema, stable IDs, canonical JSON). See ASSIGNMENTS.md.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // org.json ships inside the Android framework, so on device this is already
    // there. Declaring it `compileOnly` lets :core compile off-device without
    // shipping a second copy of the classes into the APK, where it would clash
    // with the platform's. Tests need a real implementation on the JVM.
    compileOnly("org.json:json:20240303")

    implementation(kotlin("reflect"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation(kotlin("test"))
}
