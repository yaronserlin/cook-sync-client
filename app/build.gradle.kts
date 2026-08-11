import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing credentials, loaded from a gitignored keystore.properties
// so the keystore path and passwords never get committed.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseSigning = keystorePropertiesFile.exists()
if (hasReleaseSigning) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

android {
    namespace = "com.cooksync.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.cooksync.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Selects which backend the app talks to. "dev" hits the local network server used during
    // development; "prod" hits the deployed Render instance. Switch via the Android Studio
    // Build Variants panel (or -PbuildVariant / --product-flavor on the CLI).
    flavorDimensions += "environment"

    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "CookSync Dev")
            buildConfigField("String", "BASE_URL", "\"http://192.168.0.223:8080/\"") // LOCAL_DEV_HOST: rewritten by run_project.sh to the current machine's LAN IP
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://cooksync-server.onrender.com/\"")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "src/main/res-features/auth",
                "src/main/res-features/home",
                "src/main/res-features/recipe-add",
                "src/main/res-features/recipe-favorites",
                "src/main/res-features/recipe-myrecipes",
                "src/main/res-features/recipe-common",
                "src/main/res-features/common",
                "src/main/res-features/admin",
                "src/main/res-features/settings"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // Shared DTOs — single source of truth for request/response payload shapes,
    // consumed identically by cook-sync-server (Maven) via JitPack.
    implementation(libs.cooksync.dtos)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    // Security — encrypted storage for JWT access/refresh tokens
    implementation(libs.security.crypto)

    // Lifecycle — MVVM ViewModel + LiveData
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Images
    implementation(libs.glide)
    implementation(libs.cloudinary.android)
    implementation(libs.fresco)
    implementation(libs.photoview)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
