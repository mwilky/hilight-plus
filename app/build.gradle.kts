plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mwilky.hilight.plus"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mwilky.hilight.plus"
        minSdk = 37
        targetSdk = 37
        versionCode = 13
        versionName = "1.1.9"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Debug build with R8 on, for checking the shrunk app on device without a release signing
        // step. Pick it from Build Variants in Android Studio; plain "debug" stays unminified.
        create("debugMinified") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            matchingFallbacks += listOf("debug")
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
    implementation(libs.api)
    implementation(libs.provider)
    implementation(libs.billing.ktx)
    // Pairs with and connects to the phone's own Wireless debugging, to start the daemon without
    // Shizuku. Conscrypt supplies the TLS 1.3 key export the pairing protocol needs.
    implementation(libs.libadb.android)
    implementation(libs.conscrypt.android)
    // Already pulled in by libadb at runtime; declared to build the pairing certificate.
    implementation(libs.bcprov)
    // Play Billing pulls in androidx.fragment 1.1.0 via play-services-base; force a current release.
    implementation(libs.androidx.fragment)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
