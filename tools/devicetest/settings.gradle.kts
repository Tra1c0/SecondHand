// Stand-alone Gradle project of the device-test app (stage 73). It is NOT part of the main build:
// the root settings.gradle does not include it, and it is built with
//   ./gradlew -p tools/devicetest :app:assembleDebug
// so nothing in here can change the shipped APK.
pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "secondhand-devicetest"
include(":app")
