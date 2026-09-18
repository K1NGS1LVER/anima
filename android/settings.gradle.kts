pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "anima-android"

// One module, one owner -- see ASSIGNMENTS.md. Nobody edits another module;
// everything talks through :core, which is the only horizontal dependency.
include(":core")        // Jacob   -- pack schema, stable IDs, canonical JSON, UIFormer
include(":capture")     // Samuel  -- accessibility, screenshots, scroll, window enumeration
include(":explore")     // Samuel  -- exploration policy, frontier, coverage, safety envelope
include(":understand")  // Daniel  -- LLM/VLM screen, element and journey semantics
include(":design")      // Jiya    -- brand and design-token extraction
include(":store")       // Jacob   -- SQLite, pack assembly, diffing, .animapack export
include(":app")         // Neethu  -- onboarding, scan control, viewer, Play readiness
