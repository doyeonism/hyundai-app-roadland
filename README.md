# Roadland · 로드랜드

🎬 **[로드랜드 시연 영상 보기](https://drive.google.com/file/d/1Eb5vBAzz80VG9g-SDiw7ROUNeU5HCX5F/view?usp=sharing)**

**안전한 운전으로 넓히는 나의 영토**

로드랜드는 안전한 주행을 크레딧으로 보상하고, 방문한 지역을 나만의 영토로 가꾸는 Pleos Connect 앱입니다.

주행 중에는 지나간 구간과 예상 크레딧을 간단히 보여주고, 주차 후 안전 점수·주행 요약을 확인해 땅 구매, 개인 레벨업, 수익 수령을 진행합니다. 크레딧과 소유권의 최종 처리는 별도 서버가 담당합니다.

## 주요 기능

로드랜드의 기반은 **안전한 운전**입니다. 운전 중에는 도로에 집중하고, 안전하게 주행한 결과를 주차 후 확인하여 영토를 가꾸는 흐름으로 설계했습니다. 급제동·차량 안전 경고가 기록된 구간은 주행 보상에서 제외하며, 방향지시등 사용과 꾸준한 안전 운전을 보상·기록에 반영합니다.

- 차량 속도·기어·브레이크·경고·위치 신호를 이용한 주행 요약
- D 상태의 일시 정차와 P 주차 구분, 주차 후 주행 정산
- 전국에서 지역·개별 타일까지 확대하는 내 영토 지도, 방문·소유권 표시
- 크레딧으로 땅 구매 및 개인 레벨업, 누적 수익 수령
- 주행 기록·안전 연속 기록·순위 요약
- 개인정보 동의·전체 데이터 삭제, 선택형 도착 요약 음성 안내

NaviHelper와 SpeechToText는 의존성에 포함돼 있지만 현재 앱의 핵심 흐름에서 직접 호출하지 않습니다. 지도·차량 기능의 검증 범위는 아래 테스트 환경을 기준으로 합니다.

## 안전한 운전이 영토 성장으로 이어지는 방식

**안전 주행 → 유효 구간의 크레딧 획득 → 주차 후 정산 → 땅 구매·개인 레벨업·땅 레벨업 → 내 영토 성장**이 기본 이용 흐름입니다. 보상을 위해 추가 운행이나 차선 변경을 할 필요 없이, 평소 필요한 이동에서 안전한 운전 습관을 이어가도록 구성했습니다.

### 운전 중 조작을 제한합니다

- 차량이 P가 아니고 주차 브레이크도 해제되어 있으면 전체 게임 화면을 닫고 간단한 주행 위젯으로 전환합니다. 위젯에는 주행·일시 정차 상태, 오늘 지나간 구간 수, 누적 크레딧을 표시합니다.
- 주행 화면에는 구매·레벨업·지도 탐색을 위한 버튼이 없습니다. 땅 구매, 개인·땅 레벨업, 수익 수령, 상세 기록·설정은 주차 상태의 전체 화면에서 이용합니다.
- D에서 속도만 0이 된 신호 대기는 주차로 취급하지 않습니다. 일시 정차 중에도 게임 화면을 열지 않습니다.
- 현재 주차 판정은 **P 기어 또는 주차 브레이크 작동**입니다. 차량 상태를 확인하지 못한 동안에는 확인 대기 화면을 표시합니다.
- 주행 상태로 바뀌면 열려 있던 상세 화면을 닫고 음성 출력을 중단합니다. 도착 요약 TTS는 사용자가 설정에서 켠 경우에만 주차 상태에서 재생합니다.

### 안전 신호에 따라 점수와 보상이 달라집니다

| 기능 | 현재 구현된 조건 | 결과 |
|---|---|---|
| 안전 점수 | 100점에서 급제동 1회당 15점, 차량 안전 경고 1회당 10점, 방향지시등 없는 차선 변경 추정 1회당 5점 차감 | 최저 0점·최고 100점. 도착 요약·주행 기록에서 확인 |
| 안전 구간 기본 보상 | 해당 타일에 급제동·차량 안전 경고가 기록되지 않음 | 중복을 제외한 타일당 40크레딧 |
| 위험 신호 구간 제외 | 해당 타일에 급제동 또는 차량 안전 경고가 기록됨 | 해당 타일의 기본 보상과 보조 모드 보너스 모두 0 |
| 에코·크루즈 보너스 | 보상 가능한 안전 구간에서 ECO 모드 또는 크루즈 신호가 기록됨 | 해당 구간 기본 보상의 50% 추가. 모드 사용 자체가 안전 운전을 보증하지는 않음 |
| 방향지시등 매너 보너스 | 차선 변경 추정이 1회 이상이고 방향지시등 사용 비율이 80% 이상 | 안전 구간 기본 보상과 보조 모드 보너스 합계의 20% 추가. 차선 변경이 없으면 추가 보너스 없음 |
| 안전 운전 연속 기록 | 거부되지 않은 주행에 타일이 있고 안전 점수가 90점 이상 | 연속 횟수·최고 연속 기록 갱신. 90점 미만이면 현재 연속 횟수 초기화 |
| 일일 주행 보상 상한 | 서울 날짜 기준 주행 보상 합계 | 하루 최대 8,000크레딧. 장시간 추가 운행으로 주행 보상을 무제한 쌓을 수 없음 |
| 도착 후 피드백 | 주차로 주행을 마친 뒤 정산 | 안전 점수, 급제동·경고 횟수, 방향지시등 사용, 제외 구간·보너스·실제 지급액 표시 |
| 구간별 지도 표시 | 주행 경로에 급제동·경고 구간이 있음 | 해당 구간을 경고색으로 구분하여 돌아볼 수 있음 |

예를 들어 서로 다른 타일 10칸 중 2칸에 급제동·경고가 기록되면, 기본 보상은 안전 구간 8칸 × 40 = 320크레딧입니다. 같은 구간에 ECO·크루즈 조건이 충족되면 보조 보너스를 더하고, 방향지시등 사용 조건까지 충족하면 매너 보너스를 더합니다. 최종 지급액은 서버 검증과 남은 일일 한도를 적용한 값입니다. 주행 중 위젯의 예상액은 매너 보너스·서버 검증·일일 상한이 모두 반영된 최종 정산액과 다를 수 있습니다.

### 차량 신호로 판정하는 범위

- **급제동:** 직전 속도가 6km/h 이상일 때 초당 7.5km/h 이상의 감속과 브레이크 입력이 2초 이내에 함께 감지되면 기록합니다. 안전을 위해 필요한 제동을 피해서는 안 되며, 이 기록은 운전자의 과실 판정이 아닙니다.
- **차량 안전 경고:** MASTER_WARNING, ABS, ESC, OVER_SPEED, ICY_ROAD 중 하나 이상이 켜진 상태를 묶어 기록합니다. 경고 상태로 새 타일을 지나면 그 타일도 보상 제외 대상입니다. 경고가 켜져 있는 시간 동안 매 순간 횟수를 더하지 않고, 경고 상태가 새로 시작될 때 횟수를 더합니다.
- **차선 변경 추정:** 속도 30km/h 이상, 조향각 절댓값 6도 이상, 이전 판정 이후 4초 이상이라는 조건을 사용합니다. 방향지시등 신호는 전후 3초 범위로 연계합니다. 실제 차선 인식이 아닌 신호 기반 추정이므로 회전 등도 포함될 수 있습니다. 실제 운전에서는 방향지시등을 미리 사용해야 합니다.
- **위치 이상:** 앱에서 급격한 GPS 위치 튐을 걸러내고, 서버에서도 시간·평균 속도·거리 대비 타일 수·경로 연속성·주행 시간 겹침 등 기록의 일관성을 검증합니다. 같은 주행을 재전송해도 중복 보상하지 않습니다. 이는 모든 교통법규 준수나 실제 안전 여부를 완전히 검증하는 기능은 아닙니다.

### 영토 기능과 안전 조건의 정확한 관계

안전 주행 보상으로 마련한 크레딧은 땅 구매와 개인·땅 레벨업에 사용할 수 있습니다. 다만 **현재 구현은 안전 점수 미달을 이유로 모든 게임 기능을 잠그는 방식은 아닙니다.** 낮은 점수의 주행에서도 급제동·경고가 없는 타일은 보상될 수 있으며, 방문한 타일의 구매 자격은 방문·소유권·재방문 조건으로 정합니다. 가입 지급액과 기존 잔액도 사용할 수 있습니다.

소유한 땅의 일일 수익은 땅 레벨과 경과 시간으로 계산하며, 안전 점수 때문에 중단되지는 않습니다. 방문 통계의 80점 초과 분류와 최근 7일 중 3일 방문 시 표시하는 홈 루트도 별도 통계이며, 수익 증액이나 안전 인증 조건이 아닙니다. **안전 운전을 중심으로 성장하는 이용 원칙**과 **현재 코드가 직접 제한하는 보상·조작 조건**을 구분하여 안내합니다.

구현 기준: 앱의 [Rules.kt](app/src/main/java/com/example/myapplication/Rules.kt), [TripRecorder.kt](app/src/main/java/com/example/myapplication/TripRecorder.kt), [DriveService.kt](app/src/main/java/com/example/myapplication/DriveService.kt), [MainActivity.kt](app/src/main/java/com/example/myapplication/MainActivity.kt) 및 [서버 보상 규칙](https://github.com/doyeonism/hyundai-app-roadland-server/blob/main/apps/api/src/roadland/domain/rules.ts). 수치는 현재 저장소 기준이며 실제 배포 서버의 규칙 변경에 따라 달라질 수 있습니다.

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

- 제출용 release APK: [조도연_Roadland_release.apk](apk/조도연_Roadland_release.apk)
- release 빌드: `.\gradlew.bat :app:assembleRelease` (기본 산출물은 미서명이며 별도 서명 필요)
- 기본 빌드 결과: `app/build/outputs/apk/debug/app-debug.apk`
- 2026-11-15 로컬 배포 복사본: `output/Roadland-20261115-debug.apk`
- [GitHub Releases](https://github.com/doyeonism/hyundai-app-roadland/releases)

`apk/` 바로 아래의 `*_release.apk`만 Git 추적을 허용합니다. 그 밖의 APK와 `output/`은 제외되며 로컬 복사본은 저장소를 복제해도 내려받아지지 않습니다. 제출용 파일은 별도 release 키로 서명하며 키와 비밀번호는 저장소에 포함하지 않습니다. 기존 debug 앱과 서명이 달라 바로 덮어쓸 수 없습니다. 기존 앱 삭제 시 기기 내부 토큰과 미전송 기록이 사라질 수 있으므로 별도 테스트 기기에 설치하는 것을 권장합니다. GitHub Release 게시와 저장소의 APK 제공은 구분합니다. 상세 정보는 [APK 안내](apk/README.md)를 참고하세요.

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
