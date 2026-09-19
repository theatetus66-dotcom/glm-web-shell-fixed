plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glmwebshell.features.chat"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(projects.core.common)
    api(projects.core.ui)
    api(projects.core.data)
    api(projects.pageengine)
    api(projects.adapter)

    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.navigation.compose)
    api(libs.hilt.navigation.compose)
    api(libs.androidx.activity.compose)
    api(libs.androidx.browser)

    api(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
