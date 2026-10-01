plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.smartquiz"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.smartquiz"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/*.kotlin_module"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // ✅ REQUIRED for POI on Android (java.time, java.util.stream, etc.)
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    // ================================================================
    // AndroidX & UI
    // ================================================================
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("com.google.android.material:material:1.12.0")

    // ================================================================
    // Firebase
    // ================================================================
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation("com.google.firebase:firebase-storage:22.0.1")
    implementation("com.google.firebase:firebase-messaging-ktx:24.1.2")

    // ================================================================
    // Google Sign-In
    // ================================================================
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    // ================================================================
    // Lifecycle
    // ================================================================
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ================================================================
    // Kotlin
    // ================================================================
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")

    // ================================================================
    // Image Loading
    // ================================================================
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // ================================================================
    // QR Code
    // ================================================================
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // ================================================================
    // Media & Charts
    // ================================================================
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ================================================================
    // Background Work (archive sweep)
    // ================================================================
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ================================================================
    // XLSX — Apache POI for Android (JitPack)
    // ================================================================
    // This is the Android-optimized repackaging of Apache POI.
    // Provides XSSFWorkbook for reading .xlsx files.
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // Required by POI (java.time / java.util.stream on API < 26)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // ================================================================
    // Test
    // ================================================================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}