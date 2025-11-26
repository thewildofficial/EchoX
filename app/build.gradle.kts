import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.echox.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.echox.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
        val xClientId = localProperties.getProperty("X_CLIENT_ID") ?: ""
        val xClientSecret = localProperties.getProperty("X_CLIENT_SECRET") ?: ""
        val xApiKey = localProperties.getProperty("X_API_KEY") ?: ""
        val xApiSecret = localProperties.getProperty("X_API_SECRET") ?: ""
        val xBearerToken = localProperties.getProperty("X_BEARER_TOKEN") ?: ""
        
        buildConfigField("String", "X_CLIENT_ID", "\"$xClientId\"")
        buildConfigField("String", "X_CLIENT_SECRET", "\"$xClientSecret\"")
        buildConfigField("String", "X_API_KEY", "\"$xApiKey\"")
        buildConfigField("String", "X_API_SECRET", "\"$xApiSecret\"")
        buildConfigField("String", "X_BEARER_TOKEN", "\"$xBearerToken\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "echox"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.openid:appauth:0.11.1")

    // Media3
    implementation("androidx.media3:media3-transformer:1.2.0")
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-common:1.2.0")
    implementation("androidx.media3:media3-effect:1.2.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Ktor HTTP client
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-gson:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")
    
    // SLF4J Android for app runtime
    implementation("org.slf4j:slf4j-android:1.7.36")
    
    // SLF4J Simple for unit tests (instead of Android logger)
    testImplementation("org.slf4j:slf4j-simple:1.7.36")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
