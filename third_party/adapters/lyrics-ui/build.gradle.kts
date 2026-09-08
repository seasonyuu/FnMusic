plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Compose 1.11 stabilized DeferredTargetAnimation and removed its opt-in marker.
// Filter only these obsolete lines in a generated copy; the gitlink stays pristine.
val prepareLyricsSources by tasks.registering(Sync::class) {
    from("../../accompanist-lyrics-ui/src/src/commonMain/kotlin")
    into(layout.buildDirectory.dir("generated/lyricsSources"))
    filesMatching("**/SpringPlacementModifier.kt") {
        filter { line: String ->
            if (line == "import androidx.compose.animation.core.ExperimentalAnimatableApi" ||
                line == "@OptIn(ExperimentalAnimatableApi::class)") "" else line
        }
    }
}

tasks.named("preBuild") { dependsOn(prepareLyricsSources) }

android {
    namespace = "com.mocharealm.accompanist.lyrics.ui"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main").kotlin.directories.add(layout.buildDirectory.dir("generated/lyricsSources").get().asFile.path)
        getByName("test").kotlin.directories.add("../../accompanist-lyrics-ui/src/src/commonTest/kotlin")
    }
}

dependencies {
    implementation(project(":accompanist-lyrics-core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
