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
    require(File(personalAssetsDirectory, "boards").isDirectory && File(personalAssetsDirectory, "pieces").isDirectory) {
        "lumen.personalAssetsDir must contain boards/ and pieces/ directories"
    }
}
val personalAssetsEnabled = personalAssetsDirectory != null

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

    if (personalAssetsDirectory != null) {
        sourceSets.getByName("main").assets.srcDir(personalAssetsDirectory)
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
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
