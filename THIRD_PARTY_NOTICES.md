# Third-party components

확인일: 2026-11-15. 본 파일은 실제 의존성 메타데이터와 공식 프로젝트의 라이선스 출처를 정리한 안내입니다. 앱 자체에 대한 라이선스를 부여하는 문서는 아닙니다.

## Android 앱과 테스트

| 구성요소 | 버전 | 라이선스 | 확인 출처 |
|---|---|---|---|
| AndroidX AppCompat | 1.8.0 | Apache-2.0 | 배포 POM, [AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| AndroidX Core KTX | 1.19.1 | Apache-2.0 | 배포 POM |
| Material Components Android | 1.14.0 | Apache-2.0 | 배포 POM, [LICENSE](https://github.com/material-components/material-components-android/blob/master/LICENSE) |
| AndroidX Test Ext JUnit | 1.3.0 | Apache-2.0 | 배포 POM |
| AndroidX Espresso Core | 3.7.0 | Apache-2.0 | 배포 POM |
| JUnit | 4.13.2 | EPL-1.0 | 배포 POM, [LICENSE](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt) |
| Kotlin 런타임 | 구성별 실제 해석 버전은 CSV 참고 | Apache-2.0 | [LICENSE](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) |
| javax.inject | 1 | Apache-2.0 | [공식 소스 고지](https://github.com/javax-inject/javax-inject/blob/master/src/javax/inject/Inject.java) |
| Guava ListenableFuture | 1.0 | Apache-2.0 | 상위 Guava POM / [프로젝트](https://github.com/google/guava) |
| Hamcrest Core / Library | 1.3 | BSD-3-Clause | [해당 버전 LICENSE](https://github.com/hamcrest/JavaHamcrest/blob/hamcrest-java-1.3/LICENSE.txt) |

직접·전이 의존성 107개 좌표의 목록은 [android-dependencies.csv](docs/android-dependencies.csv)에 있습니다. 범위 열은 앱 실행, 단위 테스트, 계측 테스트를 구분합니다. SDK 내부에 포함된 별도 바이너리의 모든 고지를 이 목록만으로 확인한 것은 아닙니다.

## 빌드 도구

- Gradle 9.6.0: [Apache-2.0](https://github.com/gradle/gradle/blob/master/LICENSE).
- Android Gradle Plugin 9.5.0: [Android 빌드 도구 소스](https://android.googlesource.com/platform/tools/base/), Apache-2.0 기반. 도구 배포물의 개별 고지는 별도 확인합니다.
- Gradle Wrapper의 JAR와 스크립트는 원래의 저작권·라이선스 고지를 유지합니다.

## 선택적 웹 데모

`web/`은 기존 웹 데모이며 별도 서버 저장소와 구분합니다. 잠금 파일 기준 직접·전이 패키지 목록은 [web-dependencies.csv](docs/web-dependencies.csv)에 있습니다. 각 패키지의 `license` 메타데이터를 기록했으며 배포물에 포함된 개별 저작권 고지는 해당 패키지를 확인해야 합니다.

| 직접 사용 구성요소 | 버전 기준 | 라이선스 |
|---|---|---|
| Next.js | 15.5.28 | [MIT](https://github.com/vercel/next.js/blob/canary/license.md) |
| React / React DOM | 19.1.10 | [MIT](https://github.com/facebook/react/blob/main/LICENSE) |
| server-only | 잠금 파일 | MIT |
| TypeScript | 잠금 파일 | [Apache-2.0](https://github.com/microsoft/TypeScript/blob/main/LICENSE.txt) |
| ESLint / @eslint/eslintrc / eslint-config-next | 잠금 파일 | MIT |
| Vitest | 잠금 파일 | [MIT](https://github.com/vitest-dev/vitest/blob/main/LICENSE) |
| @types/node / @types/react / @types/react-dom | 잠금 파일 | MIT |

## 지도 데이터와 경로

- © OpenStreetMap contributors. 지도 데이터는 [ODbL-1.0 및 저작권 안내](https://www.openstreetmap.org/copyright), 표준 타일은 [Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/)를 따릅니다. 앱은 영토 지도 위에 OSM 출처 문구를 표시합니다.
- 중화역→강남역 시연 경로는 OSRM의 도로 경로 응답을 바탕으로 구성했습니다. OSRM 엔진은 [BSD-2-Clause](https://github.com/Project-OSRM/osrm-backend/blob/master/LICENSE.TXT)이며 엔진 바이너리는 이 앱에 포함하지 않습니다. 경로의 기반 OSM 데이터와 타일 서비스 조건은 엔진 라이선스와 별개입니다.

## 별도 공급자 약관을 확인할 구성요소

### Pleos Connect SDK

| Maven 구성요소 | 버전 |
|---|---|
| Vehicle | 2.3.2 |
| NaviHelper | 3.0.5 |
| FusedLocation | 1.1.3 |
| TextToSpeech | 3.3.1 |
| SpeechToText | 3.3.1 |

이 SDK들과 일부 Pleos AIDL·데이터·보안 전이 구성요소의 배포 POM에는 라이선스 정보가 확인되지 않았습니다. 임의로 Apache/MIT 등으로 분류하지 않았습니다. [Pleos 개발자 문서](https://document.pleos.ai/en/docs/pleos-only/getting-started)와 실제 SDK의 배포·재배포 조건을 확인해야 합니다.

### Mapbox (별도 지도 도구)

`tools/map-preview/`는 Mapbox GL JS **3.35.0**을 공식 CDN으로 불러오며 Mapbox Standard 스타일을 사용합니다. [GL JS 라이선스](https://github.com/mapbox/mapbox-gl-js/blob/main/LICENSE.txt)와 [Mapbox 약관](https://www.mapbox.com/legal/tos/)을 따릅니다. 1.13 이하의 BSD 라이선스를 3.x 전체에 적용하지 않습니다.

Mapbox 공개 토큰은 로컬 설정에서 빌드 시 주입하며, 지도 도구 APK에 포함됩니다. 서버용 비밀 키를 대신 넣지 않습니다. 화면의 Mapbox·OpenStreetMap 출처 표시는 유지합니다.
