# Roadland release APK

**안전한 운전으로 넓히는 나의 영토**

[조도연_Roadland_release.apk 다운로드](조도연_Roadland_release.apk)

- 빌드일: 2026-11-15
- 앱 소스: 이 APK와 함께 커밋된 release 최적화 오류 수정본 (기반 커밋 `021dcef`)
- 변형: `release`, 별도 release 키 서명, debuggable 비활성
- 앱 ID: `com.example.myapplication`
- 버전: `1.0` / versionCode `1`
- 크기: 29,438,072바이트
- SHA-256: `79efb113e5cc704b96055e598722fa4b0f2c44a63621c633b9133b003ab360d7`
- 서명 인증서 SHA-256: `fb286666bc1d54666a60a1db3058aada01466afea80aed0b9f9f85db2a65b81b`
- 기본 API: `https://roadland.doyeonism.com/api/roadland/v1/car`

Pleos Connect 가상 기기와 필요한 차량·위치 권한이 필요합니다. 일반 휴대전화에서 차량 기능을 재현할 수 없습니다. 환경과 권한 설정은 [프로젝트 README](../README.md)를 참고하세요.

```powershell
adb install apk/조도연_Roadland_release.apk
adb shell am start -n com.example.myapplication/.MainActivity
```

기존 debug 설치본과 서명이 달라 덮어쓸 수 없습니다. 별도 테스트 기기에 설치하세요. 기존 앱을 삭제하면 로컬 인증 토큰과 미전송 기록이 사라질 수 있습니다. 동일 서명으로 업데이트하려면 로컬 release 키를 보관해야 합니다. 키와 비밀번호는 Git에 포함하지 않습니다.

release 빌드·서명 검증과 Pleos Connect v2.1.0 보조 기기 설치·첫 실행·권한 허용·운영 서버 연결·홈/영토/기록/설정 조회·개인 레벨업·재실행 후 상태 유지를 확인했습니다. 부분 최적화에서 발생한 시작 오류를 해결하기 위해 release 최적화를 비활성화했으며 debuggable은 비활성 상태입니다. 실제 주행 정산·땅 구매·수익 수령을 포함한 전체 시나리오는 이 release에서 아직 검증하지 않았습니다. [검증 기록](../docs/release-verification.md)을 참고하세요.
