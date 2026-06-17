import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// 릴리스 서명 자격증명을 keystore.properties 에서 읽는다(소스에 비밀 미포함).
// 파일이 없으면 release 빌드는 미서명으로 떨어진다(개발 PC 외 환경 대비).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

plugins {
    // AGP 9.x 는 Kotlin 을 내장(built-in)한다 → kotlin.android 를 따로 적용하지 않는다.
    // (적용하면 'kotlin' 확장 중복 등록으로 충돌)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.vasim.glass"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.vasim.glass"
        // Moziware Cimo = RealWear 기반 Android 10 → minSdk 29
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // BODA.VMS.Web 서버 주소.
        //  - 운영/고객 PoC: 배포된 실서버(HTTPS, 공인 인증서)
        //  - 로컬 스텁 테스트 시: 에뮬레이터 http://10.0.2.2:5000 / 실기 http://<PC LAN IP>:5000
        buildConfigField("String", "BASE_URL", "\"https://boda-vms.com\"")
        // 핸즈프리 익명 엔드포인트 보호용 키(서버 X-API-Key 필터와 일치시킬 것). 미사용 시 빈 값.
        buildConfigField("String", "API_KEY", "\"\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
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
            // keystore.properties 가 있을 때만 서명 설정을 연결한다.
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
