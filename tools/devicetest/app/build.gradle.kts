plugins {
    id("com.android.application")
}

// A tiny target app for the emulator tests: four input fields and helpers the host script needs.
// Plain Java, no dependencies. It is installed next to the keyboard in the emulator only.
android {
    namespace = "dev.personal.secondhand.devicetest"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.personal.secondhand.devicetest"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
