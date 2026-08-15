plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.klinetrain.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.klinetrain.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 固定release签名: 从环境变量读取(CI用Secrets注入, 本地可指向 keystore/ 目录);
    // 未配置时回退debug签名, 保证任何人clone后仍能直接构建安装
    val releaseKeystorePath = System.getenv("KLINETRAIN_KEYSTORE_FILE")
    val releaseSigning = if (!releaseKeystorePath.isNullOrBlank() && file(releaseKeystorePath).exists()) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeystorePath)
            storePassword = System.getenv("KLINETRAIN_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KLINETRAIN_KEY_ALIAS") ?: "klinetrain"
            keyPassword = System.getenv("KLINETRAIN_KEY_PASSWORD")
        }
    } else null

    buildTypes {
        release {
            // 有固定keystore时用正式签名(保证升级安装不冲突), 否则回退debug签名
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)

    implementation(libs.okhttp)
    implementation(libs.gson)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.common.jvm)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
