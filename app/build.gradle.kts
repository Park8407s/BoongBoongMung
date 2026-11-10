plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // Jetpack Compose 화면
    alias(libs.plugins.ksp)            // Room DB 코드 자동 생성
}

android {
    namespace = "com.drivetail.boongboongnyang"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.drivetail.boongboongnyang"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 공모전 제출용: 디버그 키로 서명해 바로 설치·실행되게 한다.
            // 앱 마켓 정식 출시 때는 별도 release 키(저장소에 올리지 않음)로 바꾼다.
            signingConfig = signingConfigs.getByName("debug")
            // 코드 줄이기(R8 최적화)는 끈다: 켜면 kotlin 라이브러리 클래스 접근 오류(IllegalAccessError)로
            // 실행하자마자 종료됨 (2026-11-05 에뮬레이터에서 확인). 출시 전 keep 규칙을 정리한 뒤 다시 검토.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Android Automotive 표준 차량 API (CarPropertyManager, CarUxRestrictionsManager)
    useLibrary("android.car")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)

    // 화면 (Jetpack Compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 로컬 DB (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Pleos Connect SDK
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)

    testImplementation(libs.junit)
}
