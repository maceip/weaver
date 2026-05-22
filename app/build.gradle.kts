plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.weaver.app"
    compileSdk {
        version = release(36)
    }

    // Release signing. CI decodes the keystore from a GitHub secret to a file
    // and exports WEAVER_KEYSTORE_FILE + the passwords; local builds without
    // those env vars fall through unsigned (fine for debug / CI smoke builds).
    val keystoreFile = System.getenv("WEAVER_KEYSTORE_FILE")
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("WEAVER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WEAVER_KEY_ALIAS")
                keyPassword = System.getenv("WEAVER_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.weaver.app"
        minSdk = 26
        targetSdk = 36
        // CI overrides these via -PweaverVersionCode / -PweaverVersionName so the
        // APK metadata matches the git tag the release workflow cuts. Local
        // builds fall back to the defaults.
        versionCode = (project.findProperty("weaverVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("weaverVersionName") as String?) ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 full mode: code shrinking + optimization + obfuscation.
            isMinifyEnabled = true
            // Strip unused resources (drawables, layouts, strings) the
            // shrunk code no longer references.
            isShrinkResources = true
            // Signed when CI supplied a keystore; otherwise unsigned.
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // Keep debug builds fast and fully debuggable — no shrinking.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // android.util.Log etc. return defaults instead of throwing in
            // plain JVM unit tests — lets us test logic that logs.
            isReturnDefaultValues = true
            // Robolectric needs the merged manifest + resources.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    debugImplementation(project(":dari"))
    releaseImplementation(project(":dari-noop"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3.expressive)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.androidx.window)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
