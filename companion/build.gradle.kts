plugins {
    // the same Android Gradle Plugin and Kotlin versions as the main build (docs/BUILD.md); the Kotlin
    // plugin is only put on the classpath here, AGP 9 compiles Kotlin with its built-in support
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
}

// Like the main build: repositories are declared per project (the local Maven mirror of the cloud
// environment, docs/HANDOFF.md, is added to the projects by an init script and replaces settings-level ones)
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
