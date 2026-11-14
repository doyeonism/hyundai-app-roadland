# 에뮬레이터 Mapbox 지도 화면

기본 내비게이션을 교체하지 않고 별도 APK로 실행하는 지도 화면입니다. 로드랜드 앱은 변경하지 않습니다.

- 앱 이름: 주행 지도 · Mapbox
- 지도: Mapbox Standard (공식 GL JS CDN)
- Android GPS 위치를 수신하여 차량 마커와 카메라를 이동합니다.
- GPS 갱신이 10초 이상 없으면 GPS 갱신 대기로 표시합니다.
- 중화역 인근 → 강남역의 사전 도로 경로를 표시합니다. 실시간 경로 탐색과 음성 안내 기능은 없습니다.
- Pleos 에뮬레이터의 P 및 D 상태에서 지도 표시를 확인했습니다. GPS 재생 중 이동·D 정차·P 도착을 녹화했습니다.

토큰은 git에서 제외한 mapbox.local.properties에 token=형식으로 설정합니다. 빌드 결과 APK에는 지도 표시를 위한 공개 토큰이 포함됩니다.

프로젝트 루트의 gradlew.bat에 -p tools/map-preview assembleDebug를 전달해 빌드합니다.
