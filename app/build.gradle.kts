plugins {
    alias(libs.plugins.android.application)
}

android {
    useLibrary("android.car")
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        // Optional isolated package for repeatable recordings; normal builds keep the production identity.
        providers.gradleProperty("roadlandApplicationIdSuffix").orNull?.let { applicationIdSuffix = it }
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 배포 서버가 기본값. 로컬 개발은 -ProadlandServer=http://10.0.2.2:3000/api/roadland/v1/car 로 지정한다.
        val server = providers.gradleProperty("roadlandServer").getOrElse("https://roadland.doyeonism.com/api/roadland/v1/car")
        buildConfigField("String", "ROADLAND_SERVER", "\"$server\"")
        // 영토 지도 배경 타일(z/x/y). 기본은 키 없이 쓸 수 있는 OSM 표준 타일인데 개발·시연용(사용 정책상 대량 사용 금지)이라,
        // 운영에서는 키가 있는 타일 서비스(VWorld, MapTiler 등) 주소로 바꾼다.
        val tiles = providers.gradleProperty("roadlandTiles").getOrElse("https://tile.openstreetmap.org/{z}/{x}/{y}.png")
        buildConfigField("String", "ROADLAND_TILES", "\"$tiles\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                // Partial optimization breaks Kotlin collection access across package scopes.
                // Keep release non-debuggable while retaining library bytecode for SDK compatibility.
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
