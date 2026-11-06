# 붕붕멍 (BoongBoongMung)

내 차에 사는 반려견 **'붕붕이'**와 함께하는 Pleos Connect 차량 라이프 앱입니다.
출근길도 주말 나들이도 붕붕이와 함께하는 하루가 되고, 기어·차속·안전벨트 같은 차량 데이터로 확인한 안전운전만큼 붕붕이가 자랍니다.
주행 중에는 화면도 소리도 없이 운전에만 집중하고, 주차하면 붕붕이가 오늘의 드라이브를 반겨 줍니다.

## APK 파일

- 위치: [`apk/금호HT_붕붕멍_release.apk`](apk/금호HT_붕붕멍_release.apk)
- release 빌드 (공모전 제출용으로 디버그 키 서명)

## 테스트 환경

- 실행 기기: **Pleos Connect 에뮬레이터 v2.1.0** (Android Automotive, API 34)
- 차량 신호 조작: **Pleos Connect Virtual Console** (아래 '시연 방법' 참고)
- 앱 창 크기: 1682 × 1210 (160dpi) — 전체 창 기준 화면이라 분할 화면(반쪽 창)은 지원하지 않습니다

## 실행 방법

1. Pleos Connect 에뮬레이터를 켭니다.
2. APK 를 설치합니다. `-g` 는 차량 권한(차속 등)을 설치할 때 한 번에 허용합니다.

```bash
adb install -r -g "apk/금호HT_붕붕멍_release.apk"
```

3. 하단 독(앱 목록)에서 붕붕멍 아이콘(강아지 얼굴)을 누릅니다.
   - `-g` 없이 설치했다면 처음 실행할 때 차속 권한을 '허용'해 주세요.
4. 앱을 한 번 열면 운행 기록 대기를 시작합니다. 이후 내비 등 다른 앱으로 화면을 바꿔도 백그라운드에서 계속 집계합니다.
   - 시동(부팅) 때 자동으로 켜지지 않습니다. 출발 전에 붕붕멍을 열어 붕붕이와 인사하세요.
   - 완전히 끄려면 기록 탭 → '붕붕멍 끄기' (다시 열면 다시 켜짐).

**필요한 권한** (매니페스트에 선언, 설치 시 허용)

| 권한 | 용도 |
|---|---|
| `android.car.permission.CAR_SPEED`, `CAR_POWERTRAIN` | 차속, 기어 |
| `pleos.car.permission.CAR_DRIVE` | 기어·브레이크·누적 주행거리 |
| `pleos.car.permission.NAVI_ROUTE` | 제한속도·목적지 도착 |
| `pleos.car.permission.CAR_SAFETY`, `PRIVATE_CAR_SAFETY`, `CAR_SEATS` | 안전벨트 |
| `FOREGROUND_SERVICE` | 앱을 연 뒤 백그라운드로 내려도 운행 집계 유지 (부팅 시 자동 시작 안 함) |

## 핵심 기능

| 단계 | 내용 |
|---|---|
| 출발 전 (주차) | 앱을 열면 붕붕이가 반겨 주고 기록 대기 시작. 운전석 안전벨트를 안 맸으면 "출발 전에 안전벨트 매라 멍!" |
| 주행 중 | 앱 화면은 쓰지 않음. 기어 D/R 진입 시 기록 시작, 백그라운드에서 차속으로 거리·급가속·급감속 집계 |
| 운행 종료 | 안전운전만큼 뼈다귀 포인트 · 선물상자 · 레벨업 · 칭호 |
| 주차 중 | 꾸미기(상점)로 붕붕이 가꾸기, 기록 확인 |

**포인트 규칙**: 주행 1km +1 · 부드러운 운전 1km +1 · 규정 속도 준수(90% 이상) +10 · 목적지 도착 +10 · 안전벨트 착용 +10 · 휴식 보너스 +20 · 연속 안전운전 일수 × 5 (최대 30). 감점은 없습니다.

## 사용 SDK

| 신호 | 출처 |
|---|---|
| 차속 | AAOS Car API `PERF_VEHICLE_SPEED` |
| 기어 | AAOS `GEAR_SELECTION` (Pleos Vehicle SDK Gear 는 예비) |
| 누적 주행거리 | Pleos Vehicle SDK Odometer (값이 없으면 차속 × 시간) |
| 브레이크 페달 | Pleos Vehicle SDK Brake (수신만, 급감속 판정은 차속 변화율 기준) |
| 안전벨트 | Pleos Vehicle SDK Safety 경고등(Telltale) — 실차 전환용 Seat 체결 상태 코드는 주석으로 포함 |
| 제한속도·목적지 도착 | Pleos NaviHelper SDK |
| 주행 규제 | AAOS `CarUxRestrictionsManager` |

모든 데이터는 차량 안의 로컬 DB(Room)에만 저장하며, 인터넷 권한을 쓰지 않습니다.

## 빌드

- Android Studio, AGP 9.4.1 / Gradle 9.6.0 / Kotlin 2.2.10
- compileSdk·targetSdk 37, minSdk 28
- Pleos SDK: `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5`

```bash
./gradlew :app:assembleRelease      # 제출용 (app/build/outputs/apk/release/)
./gradlew :app:assembleDebug        # 개발용 (adb 치트 포함)
./gradlew :app:testDebugUnitTest    # 단위 테스트 18개
```

## 시연 방법

1. **Pleos Connect Virtual Console**로 실제 차량 신호 주행
   - `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/`
   - 붕붕멍을 먼저 연 뒤(내비로 화면을 바꿔도 됨) 기어 D → ACCEL 을 1초씩 끊어 누르며 주행(부드러운 운전) → 브레이크로 정지 → 기어 P → 붕붕멍을 열면 결과 화면
   - 안전벨트: Emulation Controls > Safety > Telltales > Seat Belt (Off = 맴, On = 안 맴)
2. **주행 로그 재생 모드**: 앱 상단 '붕붕멍' 제목을 길게 누르기 → 시나리오 1~4 (약 40초)
   - 1 출근길 18km · 2 가족 나들이 60km · 3 초보 운전(급출발·급정지, 안전벨트 늦게 맴) · 4 장거리 + 휴게소 휴식
3. **adb 치트** (디버그 빌드에서만 동작, 제출한 release APK 에는 없음)

```bash
adb shell am broadcast -a com.kumhoht.boongboongmung.SIM --es gear D --ef speed 40
adb shell am broadcast -a com.kumhoht.boongboongmung.SIM --ei limit 50
adb shell am broadcast -a com.kumhoht.boongboongmung.SIM --ez arrive true
adb shell am broadcast -a com.kumhoht.boongboongmung.SIM --ez belt false
adb shell am broadcast -a com.kumhoht.boongboongmung.SIM --es gear P --ef speed 0
adb shell am broadcast -a com.kumhoht.boongboongmung.SIM --ez clear true
```

## 에뮬레이터 한계와 대응

| 기능 | 에뮬레이터 상황 | 대응 |
|---|---|---|
| 규정 속도 준수 · 목적지 도착 | 에뮬레이터 GPS NMEA 문장의 체크섬이 `*47` 고정이라 내비가 위치를 거부 → 제한속도 0, 도착 이벤트 없음 | 주행 로그 재생 모드(시연 패널)로 시연, 실차에서 `adb logcat -s Telemetry` 로 확인 |
| 급가속·급감속 | Virtual Console 페달이 누름/뗌만 있어 가감속이 고정 (ACCEL +18, BRAKE −44 km/h/s) | 판단 창을 2초 평균으로 넓힌 가상 콘솔 기준 사용. 실차 기준은 `SafeDrivingCriteria.REAL_CAR` (실차 배포 시 `DriveEngine.LIVE_CRITERIA` 한 줄 변경) |
| 안전벨트 | 좌석별 체결 상태를 바꿀 조작이 없음 (항상 false) | 안전벨트 경고등으로 판단. 실차에서는 주석 처리된 체결 상태 코드로 전환 |

## 코드 구조

[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) 참고.

```
app/src/main/java/com/kumhoht/boongboongmung/
  vehicle/   차량 신호 수집 (TelemetryCollector, SeatBeltProbe)
  drive/     운행 상태 머신, 안전운전 판정, 백그라운드 서비스
  reward/    포인트·레벨·선물상자 규칙, 아이템 카탈로그
  replay/    주행 로그 재생 시나리오
  data/      Room DB
  ui/        홈·결과·상점·기록 화면, 붕붕이 렌더러(Canvas 벡터, 디자인 시안 v3)
```
