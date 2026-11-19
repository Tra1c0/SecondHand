// Stand-alone Gradle project of the GenieX spike companion app (stage 80). It is NOT part of the main
// build: the root settings.gradle does not include it, and it is built with
//   ./gradlew -p companion :app:assembleDebug
// so nothing in here can change the keyboard APK. It is never published (docs/BUILD.md).
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "secondhand-companion"
include(":app")
