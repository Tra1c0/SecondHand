plugins {
    id("com.android.application")
}

// Stage 80: a tiny spike app that runs Qualcomm's pre-compiled Qwen3-4B on the NPU through GenieX and
// shows whether and how fast it works. Plain Android Views, German UI, one SDK adapter file.
android {
    namespace = "dev.personal.secondhand.companion"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.personal.secondhand.companion"
        minSdk = 27 // the AAR's own minSdk
        targetSdk = 35
        versionCode = 5
        versionName = "0.5-spike"
        ndk {
            abiFilters.clear()
            abiFilters.add("arm64-v8a") // the AAR ships nothing else
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // GenieXSdk.init looks for libgeniex_plugin_*.so in applicationInfo.nativeLibraryDir, so the
        // libraries must be extracted to disk (the official sample app does the same)
        jniLibs.useLegacyPackaging = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("com.qualcomm.qti:geniex-android:0.10.0")
    // the AAR declares no dependencies but uses kotlinx.coroutines (Flow) and kotlin.Result
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
