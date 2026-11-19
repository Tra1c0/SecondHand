plugins {
    id("com.android.application")
}

// A tiny target app for the emulator tests: four input fields and helpers the host script needs.
// Plain Java; the only dependency is androidx.autofill (inline chip of the fake autofill service). It is installed next to the keyboard in the emulator only.
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

dependencies {
    // Stage 73c: the inline suggestion chip of the fake autofill service (the same library and version
    // as the keyboard uses to style such chips). In this test app only.
    implementation("androidx.autofill:autofill:1.3.0")
}
