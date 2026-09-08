plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.mocharealm.accompanist.lyrics.core"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main").kotlin.directories.add("../../accompanist-lyrics-core/src/commonMain/kotlin")
        getByName("test").kotlin.directories.add("../../accompanist-lyrics-core/src/commonTest/kotlin")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
