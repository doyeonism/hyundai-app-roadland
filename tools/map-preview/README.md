# 에뮬레이터 Mapbox 지도 화면

에뮬레이터 GPS 이동을 확인하는 별도 Android 앱입니다. 기본 Pleos 내비게이션이나 로드랜드의 영토 지도를 교체하지 않습니다.

- Mapbox GL JS 3.35.0 / Mapbox Standard 사용
- Android GPS 위치에 맞춰 위치 마커와 카메라 이동
- 중화역 인근 → 강남역 사전 도로 경로 표시
- GPS 갱신이 10초 이상 없으면 갱신 대기로 표시
- Pleos 이미지의 P·D 상태에서 지도 표시, 주행·D 정차·P 도착 확인
- 실시간 경로 탐색·교통 정보·음성 안내 기능 없음

## 빌드와 실행

루트 Android 프로젝트의 SDK 설정이 준비된 상태에서 PowerShell로 실행합니다.

```powershell
Copy-Item local.properties tools/map-preview/local.properties
Copy-Item tools/map-preview/mapbox.local.properties.example tools/map-preview/mapbox.local.properties
```

생성한 `tools/map-preview/mapbox.local.properties`의 `token`을 본인 Mapbox **공개 토큰**으로 바꾸세요. 이 파일과 `local.properties`는 Git에서 제외됩니다. 토큰은 지도 도구의 APK에 포함되므로 서버용 비밀 키를 넣지 않습니다.

```powershell
.\gradlew.bat -p tools/map-preview assembleDebug
adb install -r tools/map-preview/build/outputs/apk/debug/EmulatorMapPreview-debug.apk
adb shell am start -n com.doyeonism.mappreview/.MainActivity
```

위치 권한을 허용하고 Android Emulator의 Location 기능으로 좌표를 재생합니다. 설치 후 GPS 갱신이 없으면 출발점 또는 마지막 좌표를 표시합니다. 차량 콘솔의 기어·속도와 GPS 경로 재생은 별개로 설정합니다.

서드파티 조건은 [라이선스 안내](../../THIRD_PARTY_NOTICES.md)의 Mapbox·OpenStreetMap·OSRM 항목을 확인하세요.
