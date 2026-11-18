
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    jacoco
}

jacoco {
    toolVersion = "0.8.14"
}

android {
    namespace = "com.example.trojanrideshare"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.trojanrideshare"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "USE_FIREBASE_EMULATOR", providers.gradleProperty("useFirebaseEmulator").orElse("false").get())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    // Enable BuildConfig for Firebase emulator configuration.
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-functions")
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation(libs.arch.core.testing)
    // Android's org.json is a stub in local unit tests; this is the real implementation.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

// Run with testDebugUnitTest --tests '*AccountProfileWhiteBoxTest' so these
// figures describe only Member 1's five JUnit cases, not the whole team suite.
tasks.register<JacocoReport>("accountProfileCoverage") {
    dependsOn("testDebugUnitTest")
    executionData.setFrom(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(fileTree(layout.buildDirectory.dir(
        "intermediates/javac/debug/compileDebugJavaWithJavac/classes"
    )) {
        include("com/example/trojanrideshare/service/AuthService*.class")
        include("com/example/trojanrideshare/service/RegistrationValidator.class")
    })
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Run with testDebugUnitTest --tests '*RideManagementWhiteBoxTest' so these figures
// describe only Member 2's five JUnit cases, not the whole team suite.
tasks.register<JacocoReport>("rideManagementCoverage") {
    dependsOn("testDebugUnitTest")
    executionData.setFrom(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(fileTree(layout.buildDirectory.dir(
        "intermediates/javac/debug/compileDebugJavaWithJavac/classes"
    )) {
        include("com/example/trojanrideshare/service/RideService*.class")
    })
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
