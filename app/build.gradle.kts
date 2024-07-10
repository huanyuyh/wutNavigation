plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.huanyu.position"
    compileSdk = 34
    sourceSets{
        getByName("main"){
            jniLibs.srcDirs("libs")
        }
    }
    defaultConfig {
        applicationId = "com.huanyu.position"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }
    buildFeatures{
        viewBinding = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(files("libs\\AMap3DMap_10.0.700_AMapNavi_10.0.700_AMapSearch_9.7.2_AMapLocation_6.4.5_20240508.jar"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    //Google推荐的EasyPermission库
    implementation("pub.devrel:easypermissions:3.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.0") // ViewModel扩展
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.5.0") // LiveData扩展
    implementation("androidx.room:room-runtime:2.5.0")
    implementation("androidx.room:room-ktx:2.5.0") // Room KTX for coroutine support
    kapt("androidx.room:room-compiler:2.5.0")
    implementation ("com.squareup.okhttp3:okhttp:4.9.2")
    implementation ("com.google.code.gson:gson:2.8.8")


}