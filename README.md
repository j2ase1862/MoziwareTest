# 입고위치 조회 — 스마트 글라스(Moziware Cimo) 네이티브 앱

단안 보조현실 글라스에서 **바코드 스캔 + 음성 명령**으로 입고 위치를 조회·표시하는
네이티브 Android(Kotlin) 앱. 데이터/로직은 기존 **BODA.VMS.Web(.NET)** 서버를 호출하는
얇은 클라이언트다. (설계: `스마트글라스_네이티브Kotlin_아키텍처.md`)

## 동작 흐름

1. **대기** — "스캔" 이라고 말하거나 스캔 버튼을 누른다
2. 내장 스캐너로 바코드 캡처 → 화면 표시
3. **"입고"** / **"입고제품"** 음성(또는 버튼) → 서버 조회
   `GET /api/glass/inbound-location?barcode=..&mode=..`
4. 입고 위치 텍스트를 **대형 고대비**로 표시 (1차 목표)

음성 UX는 WearHF "보이는 대로 말하기"에 의존한다 — 화면에 보이는 버튼의 `text`가
곧 음성 명령이 되므로, 버튼 라벨(`스캔`/`입고`/`입고제품`)과 핸들러가 1:1로 묶인다.

## 프로젝트 구성

| 파일 | 역할 |
|------|------|
| `app/src/main/java/io/vasim/glass/MainActivity.kt` | 진입점 — 버튼·음성·바코드·상태 전환 |
| `…/GlassApi.kt` | OkHttp + 코루틴 비동기 조회, 결과를 `GlassResult` 로 환원 |
| `…/InboundLocation.kt` | 서버 응답 DTO(kotlinx.serialization) |
| `…/BarcodeScanner.kt` | 내장 스캐너 Intent 상수(RealWear 관례값 — **교체 필요**) |
| `app/src/main/res/layout/activity_main.xml` | 854×480 가로 고대비 다크 UI |
| `app/src/main/res/xml/network_security_config.xml` | PoC용 cleartext 허용 |

주요 의존성: `okhttp`, `kotlinx-coroutines-android`, `kotlinx-serialization-json`,
`appcompat`, `material`, `constraintlayout`. `minSdk = 29`(Android 10).

## 빌드

```powershell
# Android Studio 로 열거나, CLI:
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug      # APK: app/build/outputs/apk/debug/
.\gradlew.bat :app:installDebug       # 연결된 기기/에뮬레이터에 설치
```

서버 주소는 `app/build.gradle.kts` 의 `BuildConfig.BASE_URL` 로 주입된다.
기본값 `http://10.0.2.2:5000` 은 에뮬레이터에서 호스트 PC를 가리킨다.

## 실기에서 확인·교체할 TODO

1. **`BASE_URL` / 인증서** — 실제 BODA.VMS.Web HTTPS 주소로 교체. 사설 인증서면
   `network_security_config.xml` 에 사내 CA `trust-anchors` 추가하고 cleartext 를 끈다.
   API 키를 쓰면 `BuildConfig.API_KEY` 설정(서버 `X-API-Key` 필터와 일치).
2. **바코드 Intent 상수** — `BarcodeScanner.kt` 의 `ACTION_SCAN`/`EXTRA_RESULT` 를
   Moziware Cimo 실제 문서 값으로 교체. 내장 스캐너 Intent 가 없으면
   `zxing-android-embedded` 로 대체.
3. **음성 인식 검증** — "입고"/"입고제품" 한국어 인식률 + WearHF 가 네이티브 버튼을
   자동 음성 명령으로 등록하는지(이 앱의 핵심 가정) 실기에서 확인.

## 서버 측 (별도 — 기존 BODA.VMS.Web)

이 저장소에는 포함되지 않는다. 엔드포인트 계약:

```
GET /api/glass/inbound-location?barcode={바코드}&mode={입고|입고제품}
→ 200 { itemCode, itemName, locationText, coord{ x, y, z } }
→ 404 { message: "등록되지 않은 바코드" }
```
