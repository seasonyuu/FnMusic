plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.seasonyuu.fnmusic.feature.music"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":accompanist-lyrics-core"))
    implementation(project(":accompanist-lyrics-ui"))
    testImplementation(libs.junit)
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":core:designsystem"))
    implementation(project(":core:player"))
    implementation(project(":data"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.backdrop)
    implementation(libs.backdrop.shapes)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
