import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.carsciencelab"
    compileSdk = 35

    // 주차(GEAR_SELECTION)와 UX 제한(CarUxRestrictions)을 읽는 AAOS 차량 라이브러리
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.carsciencelab"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "VEHICLE_SDK_VERSION",
            "\"${libs.versions.vehicleSdk.get()}\"",
        )
    }

    // pleos: 실제 Pleos Vehicle SDK를 쓰는 차량 빌드. 앱 안에 신호 조작 버튼이 없다.
    // simulation: 화면 개발용 모의 입력 빌드. 일반 Android 에뮬레이터에서 실행한다.
    flavorDimensions += "vehicle"
    productFlavors {
        create("pleos") {
            dimension = "vehicle"
        }
        create("simulation") {
            dimension = "vehicle"
            applicationIdSuffix = ".simulation"
            versionNameSuffix = "-simulation"
        }
    }

    // release 서명. 키와 비밀번호는 저장소 밖에 두고, 저장소에 올리지 않는 keystore.properties에서 읽는다.
    // 파일이 없으면 서명하지 않은 release가 만들어진다.
    val keystoreFile = rootProject.file("keystore.properties")
    val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// 개발용 모의 입력이 들어 있는 simulation 빌드는 release로 만들지 않는다.
androidComponents {
    beforeVariants { variantBuilder ->
        val isSimulation = variantBuilder.productFlavors.any { it.second == "simulation" }
        if (isSimulation && variantBuilder.buildType == "release") {
            variantBuilder.enable = false
        }
    }
}

dependencies {
    // Pleos Vehicle SDK는 차량 빌드에만 넣는다.
    add("pleosImplementation", libs.vehicle.sdk)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.ui.tooling)
}
