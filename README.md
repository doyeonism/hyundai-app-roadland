# Roadland · 로드랜드

**안전한 운전으로 넓히는 나의 영토**

로드랜드는 안전한 주행을 크레딧으로 보상하고, 방문한 지역을 나만의 영토로 가꾸는 Pleos Connect 앱입니다.

주행 중에는 지나간 구간과 예상 크레딧을 간단히 보여주고, 주차 후 안전 점수·주행 요약을 확인해 땅 구매, 개인 레벨업, 수익 수령을 진행합니다. 크레딧과 소유권의 최종 처리는 별도 서버가 담당합니다.

## 주요 기능

- 차량 속도·기어·브레이크·경고·위치 신호를 이용한 주행 요약
- D 상태의 일시 정차와 P 주차 구분, 주차 후 주행 정산
- 전국에서 지역·개별 타일까지 확대하는 내 영토 지도, 방문·소유권 표시
- 크레딧으로 땅 구매 및 개인 레벨업, 누적 수익 수령
- 주행 기록·안전 연속 기록·순위 요약
- 개인정보 동의·전체 데이터 삭제, 선택형 도착 요약 음성 안내

NaviHelper와 SpeechToText는 의존성에 포함돼 있지만 현재 앱의 핵심 흐름에서 직접 호출하지 않습니다. 지도·차량 기능의 검증 범위는 아래 테스트 환경을 기준으로 합니다.

## 프로젝트 구성

| 경로 | 내용 |
|---|---|
| `app/` | Pleos Connect에서 실행하는 Android 앱 |
| `tools/map-preview/` | GPS 이동 확인용 별도 Mapbox 지도 앱 |
| `web/` | 기존 Next.js 웹 데모·더미 API. 별도 운영 서버를 대체하지 않음 |
| `docs/` | 오픈소스 목록과 공개 전 점검 결과 |

## 실행 방법

### 1. 개발 환경 준비

1. 저장소를 복제하고 Android Studio에서 루트 폴더를 엽니다.
2. Android SDK Platform 37, Platform Tools, Android Emulator를 설치하고 Gradle 동기화를 실행합니다. SDK 경로는 Android Studio가 생성하는 `local.properties`로 관리합니다.
3. [Pleos 공식 설치 안내](https://document.pleos.ai/en/docs/pleos-only/getting-started)에 따라 Pleos Connect Emulator 시스템 이미지를 설치합니다. Windows/Linux용 SDK Update Site는 다음과 같습니다.

   ```text
   https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
   ```

4. Automotive AVD를 생성하고 아래 검증 환경을 참고해 실행합니다. 일반 휴대전화 에뮬레이터에는 Pleos 차량 서비스가 없어 주행 기능을 검증할 수 없습니다.
5. Gradle JDK는 Android Studio에 포함된 JBR을 사용합니다. 이 저장소의 빌드 검증에는 JBR 25.0.4를 사용했습니다.

빌드 의존성 저장소 `google()`, `mavenCentral()`, Pleos Maven은 `settings.gradle.kts`에 설정돼 있습니다. 최초 동기화에는 네트워크 접근이 필요하며, SDK 접근 조건은 Pleos 안내를 확인하세요.

### 2. 빌드·설치

Windows PowerShell, 프로젝트 루트 기준입니다. Android Studio의 Run 버튼으로 실행해도 됩니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.myapplication/.MainActivity
```

`adb`는 Android SDK의 `platform-tools`에 있습니다. 여러 기기가 연결돼 있으면 명령에 `-s DEVICE_SERIAL`을 추가하고 `DEVICE_SERIAL`을 `adb devices`에서 확인한 값으로 바꾸세요.

### 3. 첫 실행과 주행 테스트

1. 가상 차량을 P에 두고 앱을 열어 데이터 수집 동의 내용을 확인합니다.
2. 차량 속도·위치 등 필요한 권한을 현재 운전자 계정에 허용합니다.
3. 공식 Virtual Console에서 기어·속도·브레이크 등의 차량 신호를 변경하고, Android Emulator의 Location 경로 재생으로 GPS 좌표를 변경합니다.
4. D에서 속도 0은 일시 정차로 취급하며, P 전환 또는 주차 브레이크 조건에서 주행을 마무리합니다.
5. 주차 후 도착 요약과 정산 결과를 확인합니다.

GPS 경로 재생과 차량 신호 입력은 별개입니다. Location의 Start Route가 차량 속도·기어까지 자동 설정해주지는 않습니다. 주행 검증에는 두 입력을 함께 맞춰야 합니다. 권한 제공 여부는 이미지·SDK 버전에 따라 다를 수 있습니다.

## 서버와 필요한 설정

- 기본 API: `https://roadland.doyeonism.com/api/roadland/v1/car`
- 서버 저장소: [hyundai-app-roadland-server](https://github.com/doyeonism/hyundai-app-roadland-server)

서버 링크는 프로젝트 소유자가 확인한 최종 주소입니다. 비공개 저장소라면 심사자가 열람할 수 있도록 별도 접근 권한이 필요합니다.

앱은 첫 등록으로 발급받은 차량 토큰을 기기 내부에 저장하고 이후 Bearer 인증에 사용합니다. 이 앱 저장소에 서버의 DB 비밀번호나 관리자 토큰을 넣지 않습니다.

로컬 서버를 사용할 때는 서버를 먼저 실행한 후 다음과 같이 빌드합니다.

```powershell
.\gradlew.bat :app:assembleDebug '-ProadlandServer=http://10.0.2.2:3000/api/roadland/v1/car'
```

`10.0.2.2`는 Android Emulator에서 개발 PC를 가리키는 특수 주소이며 배포 VM IP가 아닙니다. 해당 주소·포트에서 이 앱과 호환되는 API가 동작해야 합니다.

| 설정 | 기본값·용도 |
|---|---|
| `roadlandServer` | 위 운영 API 주소. 빌드 시 교체 가능 |
| `roadlandTiles` | `https://tile.openstreetmap.org/{z}/{x}/{y}.png`. 영토 지도의 배경 타일 |
| `roadlandApplicationIdSuffix` | 기본은 없음. 별도 시연 설치 시 `.showcase` 등 사용 |
| `local.properties` | 개발 PC의 Android SDK 경로. Git 제외 |

기본 앱의 영토 지도에는 Mapbox 키가 필요하지 않습니다. OSM 표준 타일은 [타일 사용 정책](https://operations.osmfoundation.org/policies/tiles/)을 따라야 하며, 대량 요청·오프라인 다운로드용 서비스로 사용하지 않습니다. 운영 규모에 맞는 타일 서비스로 교체할 수 있습니다.

## 검증한 환경과 화면 규격

| 항목 | 검증값 |
|---|---|
| 개발 OS | Windows / PowerShell |
| Pleos Connect 시스템 이미지 | `release_connect_v2.1.0` |
| 이미지 OS / ABI | Android 14 / API 34 / x86_64 |
| Android Emulator | 37.2.13.0, build 16740729 |
| AVD 화면 | 2560 × 1440, 가로 16:9, 160dpi |
| AVD RAM / GPU | 4GB / host GPU |
| Gradle / Android Gradle Plugin | 9.6.0 / 9.5.0 |
| 검증 JDK / Java 소스 대상 | JBR 25.0.4 / Java 11 |
| 앱 SDK 설정 | minSdk 28 / compileSdk 37 / targetSdk 37 |
| 앱 패키지 | `com.example.myapplication` |
| 시연 영상 규격 | 1920 × 1080, 30fps, 무음 |

API 34는 **실행 이미지의 버전**이고 compileSdk·targetSdk 37은 **앱의 빌드 설정**입니다. 서로 다른 값이며, 여기서 기재한 이미지에서 실행을 확인했습니다. 배포 대상 차량·다른 이미지에서의 호환성은 별도 검증이 필요합니다.

## 테스트

일반 단위 테스트:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

`ShowcaseRecordingTest`는 로컬 시연용 서버, 도로 GPS 재생 보조 도구, 공식 Virtual Console, 별도 `.showcase` 앱 설치를 전제로 하는 수동 시연 하네스입니다. 일반 기기에서 모든 계측 테스트를 일괄 실행하면 해당 전제 조건이 충족되지 않아 실패할 수 있습니다. 이 테스트는 `main` 앱의 일반 설치 확인 절차와 분리합니다. 현재 로컬 녹화 보조 도구·원본 영상은 `/.task/`, `/output/`에서 관리하며 저장소에는 포함하지 않습니다.

### 녹화 시연의 검증 범위

- 정상·급제동 케이스: Vehicle/FusedLocation 입력을 앱이 직접 기록하고 주차 후 정산했습니다.
- 영토·구매·레벨업·수익: 실제 앱 UI/API를 사용하되, 사전 방문 데이터와 시연용 시간 가속을 사용했습니다.
- 중화역→강남역 전 구간 영상: 별도 Mapbox 지도 앱의 1배속 GPS 재생입니다. 이 장면에서는 로드랜드를 중지했으므로 전 구간의 게임 보상 정산을 증명하지 않습니다.

`tools/map-preview/`는 기본 내비게이션을 교체하지 않습니다. 해당 도구에만 Mapbox 공개 토큰이 필요합니다. 자세한 설정은 [지도 도구 README](tools/map-preview/README.md)를 참고하세요.

## APK 위치

- 기본 빌드 결과: `app/build/outputs/apk/debug/app-debug.apk`
- 2026-11-15 로컬 배포 복사본: `output/Roadland-20261115-debug.apk`
- [GitHub Releases](https://github.com/doyeonism/hyundai-app-roadland/releases)

APK와 `output/`은 Git에서 제외됩니다. 위 로컬 복사본은 저장소를 복제해도 내려받아지지 않습니다. 이 문서 작성 시 APK의 GitHub Release 게시 여부는 확인하지 않았으며, 다운로드가 필요하면 소스 빌드 또는 실제 게시된 Release를 사용하세요. 현재 제공한 파일은 디버그 서명 APK이며 운영 배포용 서명 설정은 별도입니다.

## 오픈소스와 라이선스

| 구성요소 | 사용 버전 | 라이선스 |
|---|---|---|
| AndroidX AppCompat | 1.8.0 | Apache-2.0 |
| AndroidX Core KTX | 1.19.1 | Apache-2.0 |
| Material Components for Android | 1.14.0 | Apache-2.0 |
| AndroidX Test JUnit / Espresso | 1.3.0 / 3.7.0 | Apache-2.0 |
| JUnit 4 | 4.13.2 | EPL-1.0 |
| Kotlin 런타임·Gradle 도구 | 실제 해석 버전·도구별 목록 참고 | Apache-2.0 |
| OpenStreetMap 데이터 | 외부 지도 데이터 | ODbL-1.0 및 출처 표시·타일 서비스 정책 |
| OSRM | 도로 시연 경로 생성에 사용, 엔진 자체는 앱에 포함하지 않음 | 엔진 BSD-2-Clause, 경로 기반 OSM 데이터는 별도 ODbL 조건 |
| Next.js / React / ESLint / Vitest | `web/package-lock.json` 기준 | MIT (선택적 웹 데모) |
| TypeScript | `web/package-lock.json` 기준 | Apache-2.0 (선택적 웹 데모) |

직접·전이 의존성의 버전과 라이선스는 [서드파티 안내](THIRD_PARTY_NOTICES.md), [Android 목록](docs/android-dependencies.csv), [웹 목록](docs/web-dependencies.csv)을 참고하세요.

Mapbox GL JS 3.x·Mapbox Standard는 Mapbox 약관이 적용되는 별도 서비스이며 단순 BSD/MIT 오픈소스로 표기하지 않습니다. Pleos SDK도 이 문서에서 오픈소스로 분류하지 않으며, 공급자 라이선스·개발자 약관 확인이 필요합니다. 각 SDK 버전과 공식 출처는 서드파티 안내에 기재했습니다.

이 프로젝트 자체의 재사용 라이선스는 아직 지정하지 않았습니다. 의존성의 라이선스가 프로젝트 전체에 자동 적용되는 것은 아닙니다.

## 공개 전 보안·개인정보 점검

[공개 준비 점검 결과](docs/publication-review.md)를 참고하세요. 환경설정·토큰·서명 키·녹화물·진단 파일은 Git에서 제외합니다. README에는 실명·개인 이메일·배포 VM IP를 기재하지 않습니다. 과거 커밋 작성자의 개인 이메일은 현재 파일의 검사와 별개이며, `.gitignore`만으로 제거되지 않습니다.
