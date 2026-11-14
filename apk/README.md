# Roadland release APK

**안전한 운전으로 넓히는 나의 영토**

[Doyeonism_Roadland_release.apk 다운로드](Doyeonism_Roadland_release.apk)

- 빌드일: 2026-11-15
- 앱 소스: 이 APK와 함께 커밋된 로드랜드 전용 아이콘 적용본 (기반 커밋 `9c8f829`)
- 변형: `release`, 별도 release 키 서명, debuggable 비활성
- 앱 ID: `com.example.myapplication`
- 버전: `1.0` / versionCode `1`
- 크기: 20,566,008바이트
- SHA-256: `165c95133daa7e3430cb02290f88da308b9645125a359c0ece28110297fe2b11`
- 서명 인증서 SHA-256: `fb286666bc1d54666a60a1db3058aada01466afea80aed0b9f9f85db2a65b81b`
- 기본 API: `https://roadland.doyeonism.com/api/roadland/v1/car`

Pleos Connect 가상 기기와 필요한 차량·위치 권한이 필요합니다. 일반 휴대전화에서 차량 기능을 재현할 수 없습니다. 환경과 권한 설정은 [프로젝트 README](../README.md)를 참고하세요.

```powershell
adb install apk/Doyeonism_Roadland_release.apk
adb shell am start -n com.example.myapplication/.MainActivity
```

기존 debug 설치본과 서명이 달라 덮어쓸 수 없습니다. 별도 테스트 기기에 설치하세요. 기존 앱을 삭제하면 로컬 인증 토큰과 미전송 기록이 사라질 수 있습니다. 동일 서명으로 업데이트하려면 로컬 release 키를 보관해야 합니다. 키와 비밀번호는 Git에 포함하지 않습니다.

release 빌드와 APK 서명 검증을 완료했습니다. 이 release APK를 이용한 실제 기기 전체 시나리오 검증은 아직 수행하지 않았습니다.
