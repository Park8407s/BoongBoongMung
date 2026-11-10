# 붕붕냥 코드 구조 설명서

> 심사 대비용 문서. 기획서 v03의 '④ 모듈 구성'과 실제 코드를 1:1로 맞춰 정리했다.
> 유니티 개발 경험을 기준으로 비유를 달았다.

## 1. 한눈에 보기

```
 차량 신호                         집계 (주행 중, 화면 없음)                    보상 (운행 종료 시 한 번)
 ─────────                         ───────────────────────                    ─────────────────────
 Car API 차속 ─┐
 Car API 기어 ─┤                    ┌─ DriveStateMachine (출발 전/주행/종료)
 Vehicle 브레이크┼─ TelemetryCollector ─ DriveEngine ─┤                           ┌─ BoongRepository (Room)
 Vehicle 오도미터┤   (0.5초 샘플)                       └─ SafeDrivingEvaluator ──▶ RewardCalculator ─┤  포인트·선물상자·레벨·칭호
 NaviHelper ───┤                                        (급가감속·0.2km 구간·제한속도)               └─ 결과 화면
 UxRestrictions┘
                     ▲ 주행 로그 재생 모드 (ReplayScenarios) 가 같은 파이프라인으로 들어온다
```

## 2. 기획서 모듈 ↔ 코드

| 기획서 모듈 | 코드 | 유니티 비유 |
|---|---|---|
| Drive State Machine | `drive/DriveStateMachine.kt` | 상태 머신 (순수 로직) |
| Telemetry Collector | `vehicle/TelemetryCollector.kt` | 센서 입력 매니저 |
| Safe Driving Evaluator | `drive/SafeDrivingEvaluator.kt` | 판정 시스템 (순수 로직) |
| Point & Reward Engine | `reward/RewardRules.kt`, `reward/Catalog.kt`(선물상자) | 보상 테이블 (순수 로직) |
| Shop & Inventory | `data/BoongRepository.kt` (`buy`, `equip`, `openGiftBox`) | 인벤토리 매니저 |
| Pet Renderer | `ui/pet/PetRenderer.kt` (Canvas 벡터, 디자인 캔버스 붕붕냥 시안) | 레이어 스프라이트 합성 |
| Restriction Guard | `ui/Dialogs.kt` `DrivingGuard` + 매니페스트 | 입력 차단 레이어 |
| Repository | `data/BoongDatabase.kt`, `data/Entities.kt` | 세이브 시스템 |
| (집계 실행) | `drive/DriveEngine.kt`, `drive/DriveService.kt` | 게임 루프 + DontDestroyOnLoad |
| 주행 로그 재생 모드 | `replay/ReplayScenarios.kt` | 리플레이 시스템 |

## 3. 주행 상태별 동작

| 상태 | 판단 | 화면 | 소리·알림 |
|---|---|---|---|
| 출발 전 | 기어 P | 홈·상점·꾸미기 | 없음 (효과음 미사용) |
| 주행 중 | 기어 D/R (기어를 못 읽으면 주행 규제 + 차속) | 플랫폼 주행 규제가 앱을 가림. 앱도 `DrivingGuard`만 표시 | 없음. 포그라운드 서비스 알림은 중요도 최소(무음) |
| 운행 종료 | 기어 P + 차속 0 | 결과 화면 (앱이 열려 있었으면 자동, 아니면 다음 실행 시 먼저) | 헤드업 알림 미사용 |

- 매니페스트의 `MainActivity` 에 `distractionOptimized` 를 **선언하지 않았다** → 주행 중에는 시스템이 화면을 가린다.
- 주행 중 집계는 `DriveService`(포그라운드 서비스, specialUse) 가 프로세스를 살려 두고 `DriveEngine` 이 계속한다.
- 집계는 사용자가 앱(`MainActivity`)을 열 때 시작한다 (기획서의 "출발 전 인사"). 부팅(시동) 자동 시작은 하지 않으며, 앱을 백그라운드로 내리거나 내비를 전체 화면으로 써도 계속된다. 기록 탭 "붕붕냥 끄기"로 완전히 멈춘다.

## 4. 차량 신호

| 신호 | 1순위 | 2순위 / 비고 |
|---|---|---|
| 차속 | AAOS `PERF_VEHICLE_SPEED` (m/s → km/h) | 위험 권한 → 실행 시 사용자 허용 |
| 기어 | AAOS `GEAR_SELECTION` | Pleos `Gear` (SDK 에서 deprecated, Google 속성 사용 권장) |
| 브레이크 | Pleos `Brake.registerBrakePedalStatus` | 수신만. 급감속 판정은 차속 변화율 기준 |
| 누적 주행거리 | Pleos `Odometer.registerDistanceDriven` (실차에서 값이 움직일 때 우선) | 기본은 차속 × 시간 적분 |
| 제한속도·도착 | Pleos `NaviHelper` `onCurrentRoadSpeedLimit`, `onDestinationArrived` | 에뮬레이터는 재생 모드로 시연 |
| 주행 규제 | `CarUxRestrictionsManager` | |

- 리스너는 값이 '바뀔 때만' 온다. 일정 속도로 달리는 동안에도 거리를 적분하려고 `DriveEngine` 이 0.5초마다 마지막 값으로 샘플을 만든다.
- 권한이 없거나 차종이 지원하지 않으면 `register…()` 가 예외를 던진다 → 항목별로 감싸고, 지원하는 항목만 쓴다 (capability 확인).
- Pleos SDK 리스너 인자는 nullable (`Boolean?`, `Float?`, `VehicleGear?`) 이다.

## 5. 안전운전 판단 기준 (`SafeDrivingCriteria`, 테스트 후 조정)

| 항목 | 기준 |
|---|---|
| 급출발 | 출발 속도 5km/h 이하에서 초당 10km/h 이상 가속 |
| 급가속 | 초당 가속 저속(<30) 12 · 중속(30~60) 10 · 고속(≥60) 8 km/h |
| 급감속 | 초당 감속 15km/h (60km/h 이상에서는 14) |
| 급정지 | 감속 후 5km/h 이하, 초당 14km/h 이상 감속 |
| 부드러운 운전 구간 | 0.2km(시연용) 동안 급가속·급감속 0회 |
| 규정 속도 준수 | 제한속도 정보가 있는 거리 중 제한속도 이하로 달린 거리 비율 (1km 이상일 때만) |

같은 종류 이벤트는 3초 안에 한 번만 센다 (한 번의 급가속이 여러 번 잡히지 않게).

## 6. 포인트 (`PointRules`)

| 항목 | 기준 |
|---|---|
| 기본 주행 | 0.2km당 +1 (시연용) |
| 부드러운 운전 | 부드러운 0.2km 구간당 +1 |
| 규정 속도 준수 | 90% 이상 +10 |
| 목적지 도착 | +10 |
| 휴식 보너스 | 2시간 이상 연속 운행 후 15분 이상 쉬고 다시 출발 → 다음 결과에 +20 |
| 연속 안전운전 | 하루 첫 안전운전(부드러운 비율 70% 이상) 때 연속 일수 × 5 (최대 +30) |
| 선물상자 | 누적 안전운전 거리 2km마다 1개 (시연용). 확률 공개, 구매 불가 |
| 레벨 | 누적 안전운전 거리 1·3·6·10·16·24·34·46·60km, 이후 20km마다 (시연용) |
| 칭호 | 레벨 칭호 + 최근 7일 부드러운 운전 70% 이상(2km 이상) → '부드러운 드라이버' + 전용 리본 |

감점은 없다. 아쉬운 운전은 결과 화면에서 다음 목표로 안내한다.

## 7. 실행·시연

- **실행**: Android Studio 기기 선택 `Pleos_Connect_v2.1.0` → ▶ Run. 처음 실행 시 차속 권한 허용.
- **실제 신호로 시연**: Virtual Console → Gear **D** → Speed 올리기(천천히/급하게) → Gear **P** + Speed 0 → 결과 화면
- **주행 로그 재생 모드**: 상단 **'붕붕냥' 제목을 길게 누르기** → 시나리오 1~4 재생 (약 40초)
  1. 출근길 18km (기획서 그림 2와 같은 결과: +18 +15 +10 +10)
  2. 가족 나들이 60km → 선물상자 30개 (15% 확률로 선물상자 전용 선글라스)
  3. 초보 운전 → "부드러운 운전 구간이 n%였어요" 안내
  4. 장거리 2시간 10분 + 휴게소 16분 → 휴식 보너스 +20
- **adb 치트** (디버그 빌드)
  ```
  adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --es gear D --ef speed 40
  adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --ei limit 50
  adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --ez arrive true
  adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --es gear P --ef speed 0
  adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --ei replay 2
  adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --ez clear true
  ```
- **로그**: Logcat 에서 `DriveEngine`, `Telemetry` 검색
- **단위 테스트** (PC, 에뮬레이터 불필요): `./gradlew :app:testDebugUnitTest`
  - `DriveLogicTest`: 상태 머신, 거리 적분, 급출발·급정지, 부드러운 가감속, 제한속도 비율, 재생 시나리오 4종
  - `RewardTest`: 출근길 정산(0.2km 단위), 짧은 주행 포인트, 연속 일수, 선물상자, 휴식 보너스, 레벨, 선물상자 확률

## 8. 알려진 한계 / 다음 단계

- 주행 중 앱 프로세스가 강제 종료되면 진행 중인 운행은 저장되지 않는다 (다음 단계: 1분마다 중간 저장).
- 캐릭터·아이템은 코드로 그린 임시 그래픽이다. 일러스트가 나오면 `PetRenderer` 의 레이어 함수만 이미지로 교체.
- 효과음은 아직 없다 (주차 중에만 재생하도록 넣을 예정).
