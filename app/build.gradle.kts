plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.greenbuddy.doh_approvedherb_identifier"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.greenbuddy.doh_approvedherb_identifier"
        minSdk = 26 // <--- Change this value
        targetSdk = 36
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        mlModelBinding = true
        viewBinding = true
    }
    aaptOptions {
        noCompress += listOf("tflite")
    }
    androidResources {
        noCompress("tflite")
    }

}


dependencies {

    implementation("androidx.cardview:cardview:1.0.0")

    implementation("androidx.core:core-splashscreen:1.0.1")


    implementation(libs.vision.common)
    implementation(libs.androidx.recyclerview)
    val cameraxVersion = "1.5.2"

    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")

    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("com.google.mlkit:object-detection-custom:17.0.2")

    // MISSING LIBRARY: This contains PreviewView
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // TensorFlow Lite dependencies
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.mlkit:image-labeling-custom:17.0.1")

    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.1.0")// Check for latest version
    implementation("org.tensorflow:tensorflow-lite:2.15.0") // Or the latest version
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4") // Or the latest version (provides TensorBuffer, DataType etc.)
    implementation("org.tensorflow:tensorflow-lite-gpu:2.15.0") // Optional: for GPU delegation, match TF Lite version
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.3") // Optional: if using Task Library for vision tasks

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.camera.lifecycle)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

