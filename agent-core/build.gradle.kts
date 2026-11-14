plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // stage 41: phone number formatting of the format guard (Apache-2.0, metadata only, no geocoder/carrier)
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.40")
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}
