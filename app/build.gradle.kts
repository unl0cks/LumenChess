import dev.lumenchess.build.PersonalAssetDiscovery
import java.net.URI
import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val physicalArm64Only = providers.gradleProperty("lumenPhysicalArm64Only")
    .map(String::toBoolean)
    .orElse(false)

val personalAssetsProperty = providers.gradleProperty("lumen.personalAssetsDir").orNull
val personalAssetsDirectory = personalAssetsProperty?.let(::file)
if (personalAssetsDirectory != null) {
    require(personalAssetsDirectory.isDirectory) {
        "lumen.personalAssetsDir must point to an existing directory: ${personalAssetsDirectory.absolutePath}"
    }
    require(File(personalAssetsDirectory, "pieces").isDirectory) {
        "lumen.personalAssetsDir must contain a pieces/ directory"
    }
}
val personalAssetInventory = personalAssetsDirectory?.toPath()?.let(PersonalAssetDiscovery::discover)
val personalAssetsEnabled = personalAssetInventory?.styles()?.isNotEmpty() == true
val generatedPersonalAssets = layout.buildDirectory.dir("generated/lumenPersonalAssets")
val stageLumenPersonalAssets = tasks.register<Sync>("stageLumenPersonalAssets") {
    into(generatedPersonalAssets)
    personalAssetInventory?.copies()?.forEach { entry ->
        val relativeDestination = entry.relativeDestination()
        from(entry.source().toFile()) {
            into(relativeDestination.substringBeforeLast('/'))
            rename { relativeDestination.substringAfterLast('/') }
        }
    }
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

// Inter Tight is OFL-1.1. Pin the exact upstream source so public builds never float with HEAD.
// Keep generated font bytes outside Git history; deleting .gradle also invalidates configuration cache,
// so the next configuration recreates this resource directory deterministically.
val interTightUpstreamCommit = "c194f94c60b569b47876811321f5ef1f0c2614a2"
val generatedTypographyResDir = layout.projectDirectory.dir(".gradle/lumenTypography/res")
val generatedTypographyFontDir = generatedTypographyResDir.dir("font").asFile.apply { mkdirs() }
mapOf(
    "inter_tight_regular.ttf" to "InterTight-Regular.ttf",
    "inter_tight_medium.ttf" to "InterTight-Medium.ttf",
    "inter_tight_semibold.ttf" to "InterTight-SemiBold.ttf",
    "inter_tight_bold.ttf" to "InterTight-Bold.ttf",
).forEach { (localName, upstreamName) ->
    val destination = File(generatedTypographyFontDir, localName)
    if (!destination.isFile) {
        val temporary = File(generatedTypographyFontDir, "$localName.download")
        val url =
            "https://raw.githubusercontent.com/googlefonts/inter-gf-tight/" +
                "$interTightUpstreamCommit/fonts/ttf/$upstreamName"
        URI.create(url).toURL().openStream().use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        require(temporary.length() > 300_000L) {
            "Downloaded Inter Tight resource is unexpectedly small: $upstreamName"
        }
        require(temporary.renameTo(destination)) {
            "Could not install generated Inter Tight resource: ${destination.absolutePath}"
        }
    }
}

android {
    namespace = "dev.lumenchess"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "dev.lumenchess"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "LUMEN_PERSONAL_ASSETS", personalAssetsEnabled.toString())
        buildConfigField(
            "String",
            "LUMEN_PERSONAL_PIECE_STYLES",
            buildConfigString(personalAssetInventory?.encodedStyles().orEmpty()),
        )
        buildConfigField(
            "String",
            "LUMEN_PERSONAL_ASSET_FINGERPRINT",
            buildConfigString(personalAssetInventory?.fingerprint().orEmpty()),
        )

        if (physicalArm64Only.get()) {
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main").res.srcDir(generatedTypographyResDir.asFile)

    sourceSets.getByName("main").assets.srcDir(generatedPersonalAssets.get().asFile)

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.named("preBuild").configure {
    dependsOn(stageLumenPersonalAssets)
}

dependencies {
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    debugImplementation(platform(libs.compose.bom))

    implementation(project(":core-chess"))
    implementation(project(":game-runtime"))
    implementation(project(":engine-api"))
    implementation(project(":engine-host"))
    implementation(project(":data-persistence"))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
