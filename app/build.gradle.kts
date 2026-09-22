plugins {
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

import java.util.Properties

val localSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

fun signingProperty(name: String): String? =
    providers.gradleProperty(name).orNull ?: localSigningProperties.getProperty(name)

val gitVersionProperties = providers.exec {
    commandLine("bash", rootProject.file("scripts/git-version.sh"))
}.standardOutput.asText.map { output ->
    Properties().apply {
        load(output.byteInputStream())
    }
}

fun Provider<Properties>.requiredGitVersionProperty(name: String): String =
    get().getProperty(name) ?: error("git-version.sh did not provide the required property: $name")

val releaseVersionName = gitVersionProperties.requiredGitVersionProperty("versionName")
val releaseVersionCode = gitVersionProperties.requiredGitVersionProperty("versionCode").toInt()
val signingStoreFile = signingProperty("signingStoreFile")
val signingStorePassword = signingProperty("signingStorePassword")
val signingKeyAlias = signingProperty("signingKeyAlias")
val signingKeyPassword = signingProperty("signingKeyPassword")
val hasCiSigningConfig = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.seasonyuu.fnmusic"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.seasonyuu.fnmusic"
        minSdk = 26
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    if (hasCiSigningConfig) {
        signingConfigs {
            create("ciRelease") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            if (hasCiSigningConfig) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasCiSigningConfig) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
        }
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.aboutlibraries.core)
    testImplementation(libs.mockwebserver)
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:player"))
    implementation(project(":data"))
    implementation(project(":feature:session"))
    implementation(project(":feature:music"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

aboutLibraries {
    collect {
        configPath = rootProject.file("config/aboutlibraries")
        fetchRemoteLicense = false
        fetchRemoteFunding = false
        includePlatform = false
    }
}
