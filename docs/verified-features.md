# 확인한 것과 확인하지 못한 것

2026-11-15 밤 기준이다. 같은 날 처음으로 Gradle 빌드를 돌렸고, 화면을 에뮬레이터에 띄워 확인했다(8절). 시연 순서는 [demo.md](demo.md)에 초안이 있다.

## 1. 요약

| 구분 | 상태 |
|---|---|
| 학습 엔진, 진행 제어, 차량 게이트웨이 규칙 | JVM 테스트로 확인. Gradle에서 두 구성 모두 145개 통과 |
| SDK를 직접 부르는 코드(`PleosVehicleLink`, `CarSafetyMonitor`) | 실제 Vehicle SDK 2.3.2와 `android.car` 라이브러리로 컴파일됨. 실행해 보지는 못함 |
| Gradle 빌드 | 두 구성의 debug APK 빌드 확인 |
| 화면 | `simulationDebug`를 일반 Android 에뮬레이터에 띄워 확인(8절) |
| Pleos Connect 에뮬레이터, 실차 동작 | 확인하지 못함 |

2026-11-15 21시대에 macOS(Apple Silicon), JDK 17, Android SDK Platform 35, Build-Tools 35.0.0, Gradle 8.10.2에서 처음 빌드했다.
`assembleSimulationDebug`, `assemblePleosDebug`, `testSimulationDebugUnitTest`, `testPleosDebugUnitTest`가 모두 통과했다.
Vehicle SDK는 Pleos Nexus에서 받아졌다. 컴파일 경고는 `CarSafetyMonitor.kt`의 `unregisterCallback` deprecated 한 건이다.
SDK 호출 형태(4절)는 고치지 않고 그대로 컴파일됐다. 컴파일이 됐다는 것은 서명이 맞다는 뜻이고, 실행 중 동작까지 확인했다는 뜻은 아니다.

## 2. 확인한 것

| 방법 | 대상 | 결과 |
|---|---|---|
| Gradle 빌드 (2026-11-15) | `assembleSimulationDebug`, `assemblePleosDebug` | 통과. `app/build/outputs/apk/` 아래에 debug APK 두 개가 만들어짐 |
| Gradle 단위 테스트 (2026-11-15) | `testSimulationDebugUnitTest`, `testPleosDebugUnitTest` | 각각 145개 통과 |
| 일반 Android 에뮬레이터 실행 (2026-11-15) | `simulationDebug`의 화면과 흐름 | 8절 |
| JVM 단위 테스트 145개 (kotlinc 2.0.0, JUnit 4.13.2) | 학습 엔진, 관찰 해석, 진행 제어, 콘텐츠, 기록, 학습 허용 정책, 오류 분류, 차량 게이트웨이, 차량 실험 흐름 | 모두 통과 |
| 변형 검사 24개 | 차량 게이트웨이와 오류 분류의 규칙을 하나씩 깨뜨림 | 모두 테스트가 잡음 |
| 변형 검사 18개 (학습 엔진 초안 단계에 실행) | 학습 엔진, 관찰 해석 | 모두 테스트가 잡음 |
| 대역 컴파일 | Android, Compose, Pleos SDK를 쓰는 코드 전체. `pleos`, `simulation` 두 구성 | 통과 |
| 호출부 대조 | 화면과 Android 쪽 파일(`ui/`, `MainActivity`, `LabViewModel`, DataStore 저장소, `simulation`의 조작 패널)의 Compose·AndroidX 호출을 하나씩 읽어 API 서명과 대조. 실험적 API(opt-in 필요)는 쓰지 않음 | 고칠 곳을 찾지 못함 |
| Compose 문맥 규칙 | `@Composable` 호출이 `@Composable` 문맥 안에만 있는지. `@Composable`을 `suspend`로 바꿔 Kotlin 컴파일러의 suspend 호출 규칙으로 대신 검사(두 구성). 일부러 넣은 위반 1건이 잡히는 것도 확인 | 통과 |
| 리소스 참조 | 리소스 XML과 Manifest의 `@종류/이름` 참조, 문자열의 형식 지정자와 이스케이프 | 풀리지 않는 참조 없음 |

아래 줄(JVM 단위 테스트부터)은 빌드 환경이 없던 때에 컴파일러와 JUnit을 직접 불러 확인한 기록이다. 지금은 위의 Gradle 결과가 그 자리를 대신한다.

대역 컴파일은 필요한 API 선언만 직접 적어 놓고 그에 맞춰 컴파일해 본 것이다.
빠진 import, 파일 사이의 타입 불일치, 없는 문자열 리소스 참조는 잡지만 실제 SDK와 서명이 같다는 보장은 아니다.
호출부 대조도 실제 라이브러리로 컴파일한 것이 아니다. 사람이 코드를 읽어 확인한 것과 같은 수준이다.
Compose 문맥 규칙 점검은 Compose 컴파일러를 돌린 것이 아니다. 대역 선언에 적은 람다의 종류가 실제 API와 같을 때만 의미가 있다.

## 3. 차량 연동 구조

```
LabController ─ VehicleGateway ─ SdkVehicleGateway ─ VehicleLink ─ PleosVehicleLink ─ Pleos Vehicle SDK
              └ SafetyMonitor ── CarSafetyMonitor ─ AAOS Car API
```

- `PleosVehicleLink`는 SDK 호출을 그대로 옮기기만 한다. 조회용 API 네 개만 부른다.
- `SdkVehicleGateway`가 시간 제한, 다시 연결, 값 해석, 오류 분류를 맡는다. SDK 없이 테스트한다.
- `CarSafetyMonitor`는 기어 P와 UX 제한을 읽는다. 하나라도 읽지 못하면 학습을 잠근다.
- `simulation` 구성은 이 경로를 쓰지 않고 `DemoVehicleGateway`, `DemoSafetyMonitor`를 쓴다.

## 4. SDK 호출 형태의 근거

실제 SDK로 컴파일하지 못했으므로, 쓰는 호출마다 근거를 남긴다.

| 호출 | 근거 |
|---|---|
| `Vehicle(context)`, `initialize()`, `release()`, `getTurnSignal()` | 공식 Sample App, 공식 문서 Introduction |
| `checkTurnSignalStateCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | A, B |
| `getCurrentTurnSignalState(onComplete: (TurnSignalValue?) -> Unit, onFailed: (Exception) -> Unit)` | A, C |
| `registerTurnSignalState(listener)`, `unregisterTurnSignalState(listener)` | A, B, C |
| `TurnSignalStateListener.onTurnSignalStateUpdated(state: TurnSignalValue?)`, `onFailed(e: Exception)` | A, B, C |
| `CapabilityConfig.accessType`, `AccessType`(NONE, READ, WRITE, READ_WRITE) | A |
| `TurnSignalValue`(OFF, RIGHT, LEFT) | A |
| ErrorCode 이름 9개 | A |
| 권한 `pleos.car.permission.CAR_LIGHTS` | 공식 Sample App Manifest, A |
| `Car.createCar`, `getCarManager`, `CarUxRestrictionsManager`, `CarPropertyManager.registerCallback`·`getIntProperty`, `CarPropertyValue.status`, `VehicleGear.GEAR_PARK` | Android Car API. C, D, E의 사용 형태와 같음 |
| 권한 `android.car.permission.CAR_POWERTRAIN` | D, E의 Manifest |

- 공식 Sample App: <https://github.com/Pleos-Playground/pleos-sample-vehicle-app>
- 공식 문서 Introduction: <https://document.pleos.ai/en/api-reference/connect-sdk-pleos/Vehicle/intro>
- A: 공식 API Reference를 옮겨 적은 공개 저장소 문서. <https://github.com/june0103/H_Mate> 의 `docs/pleos/api/vehicle.md` (2026-11-05 수집, AAR과 이름을 대조했다고 적혀 있음)
- B: <https://github.com/CHAGOKCHAGOK-DEV/Client> 의 `PleosVehicleGateway.kt`
- C: <https://github.com/softride-app/softride> 의 `LiveVehicle.kt`
- D: <https://github.com/duqorrhdrks/teum-pleos-app>
- E: <https://github.com/yeopjari-app/yeopjari26>

A~E는 같은 SDK를 쓰는 다른 팀의 공개 저장소다. 호출 형태를 대조하는 데만 썼고 코드는 가져오지 않았다.

## 5. 공개 기록에서 알게 된 에뮬레이터 동작

직접 확인한 것이 아니다. 다른 팀이 Pleos Connect Emulator 2.1.0에서 기록한 내용이며, 이 앱의 처리 방식을 정하는 데 참고했다.

| 기록 | 출처 | 이 앱의 처리 |
|---|---|---|
| `initialize()` 직후 바로 호출해도 응답이 온다 | A의 `11-sdk-verification.md` | 초기화 뒤 기다리지 않고 요청한다 |
| 리스너를 등록하면 현재값으로 한 번 콜백이 온다(문 위치) | 같은 문서 | 알림이라고 해서 변화로 단정하지 않는다. 과제 시작 뒤 목표가 아닌 상태를 확인한 다음의 목표 상태만 변화로 인정한다 |
| 부팅 뒤 한 번도 조작하지 않은 방향지시등은 값이 오지 않는다 | D의 README | 값이 없으면 꺼짐으로 바꾸지 않는다. 아래 6-4 참고 |
| 미지원 요청의 `onFailed`가 오류 코드 없는 일반 `Exception`으로 온다 | A의 `11-sdk-verification.md` | 오류 코드가 없으면 메시지의 `not supported`로 미지원을 가린다 |
| 방향지시등을 켜면 시스템이 사각지대 카메라 창을 화면 왼쪽 위에 띄운다 | D의 README | 대비한 코드 없음. 아래 6-6 참고 |
| 기어 P는 UX 제한 없음, D는 속도 0이어도 제한. 제한 중에는 시스템이 화면을 가린다 | A의 README | `distractionOptimized`를 선언하지 않고, 앱도 따로 잠근다 |
| 기어를 표준 `GEAR_SELECTION`으로만 읽는 앱에서 기어 이벤트를 받았다. SDK 2.3.2의 기어 리스너는 표준 속성을 쓰라며 deprecated | D의 README와 코드 | 주차 확인에 `GEAR_SELECTION`만 쓴다 |

## 6. 에뮬레이터에서 먼저 확인할 것

logcat 태그는 `CarScienceLab.Vehicle`, `CarScienceLab.Safety`다.

1. **빌드.** `./gradlew assemblePleosDebug`. 2026-11-15에 통과했다. 환경이 달라 컴파일 오류가 난다면 SDK를 직접 부르는 `PleosVehicleLink.kt`와 `CarSafetyMonitor.kt`부터 본다.
2. **잠금 해제.** 기어 P에서 앱을 열었을 때 홈 화면이 나오는지 본다. 잠겨 있으면 `gear initial value=`와 `ux restriction state=` 로그를 본다. 기어를 읽지 못하면 앱은 계속 잠긴다.
3. **지원 확인.** 홈 카드가 차량 관찰 가능으로 바뀌고 `capability turn_signal_state accessType=READ`(또는 `READ_WRITE`)가 찍히는지 본다.
4. **현재값 조회.** 실험을 열면 `read turn_signal_state raw=OFF`가 찍히는지 본다. `raw=null`이거나 `read failed … code=ERROR_EMPTY_RESULT`이면 값이 아직 없는 상태다. 이때는 처음 켠 왼쪽 방향지시등을 "이미 켜져 있어요"로 안내하고, 끄고 다시 켜야 관찰로 인정한다. 시연 전에 방향지시등을 한 번 켰다 끄면 이 경로를 피할 수 있다.
5. **변화 알림.** Virtual Console에서 왼쪽을 켜면 `update turn_signal_state raw=LEFT`가 찍히고 관찰 단계로 넘어가는지 본다.
6. **사각지대 카메라 창.** 방향지시등을 켰을 때 뜨는 시스템 창 때문에 앱이 멈춤 화면으로 바뀌는지 본다. 앱은 `onStop`을 받으면 학습을 멈추고 구독을 해제한다. 전달받은 캡처(9절)에서는 카메라 창이 떠 있는 동안에도 이전 버전 앱의 화면이 관찰 결과로 바뀌어 있었다. 이 앱에서도 그런지는 확인하지 못했다. 카메라 창은 앱 창의 왼쪽 위를 덮으므로, 관찰 결과와 다음 버튼이 그 아래쪽에 그대로 보이는지도 함께 본다.
7. **주행 제한.** 기어를 D로 바꾸면 화면이 가려지고, P로 돌아오면 자동으로 이어지지 않고 "이어 하기"가 나오는지 본다.
8. **해제.** 실험에서 나가면 `unregistered turn_signal_state`가 찍히는지 본다.

## 7. 알려진 한계

- 차량 서비스가 답하지 않을 때의 처리(시간 제한 5초, 다음 요청 때 다시 연결)는 가짜 SDK 접점으로만 시험했다. 실제 SDK가 `release()` 뒤 다시 `initialize()`하는 것을 허용하는지는 확인하지 못했다.
- `pleos.car.permission.CAR_LIGHTS`가 설치할 때 부여되는지 에뮬레이터에서 확인하지 못했다. 공식 Sample App의 Manifest 주석은 Vehicle Service의 조회 권한이 `protectionLevel="normal"`이라고 적고 있고, Sample App도 선언만 하고 실행 중 요청을 하지 않는다. 이 앱도 선언만 한다.
- 차량 빌드는 차량용 기기(Pleos Connect 에뮬레이터, 실차)에서만 쓴다. 차량용이 아닌 기기에서는 차량 라이브러리를 건드리지 않고 잠긴 채로 두도록 만들었지만(`automotive feature missing`), 그 기기에서 Pleos SDK가 어떻게 동작하는지는 확인하지 못했다. 화면만 보려면 `simulation` 구성을 쓴다.
- 실차에서는 어떤 것도 확인하지 못했다.
- 화면은 일반 Android 에뮬레이터(API 33)에서만 봤다. Pleos Connect의 앱 창 크기와 화면 밀도는 캡처에서 어림한 값이어서, 실제 에뮬레이터에서 글자 크기와 배치를 다시 봐야 한다.

## 8. 화면 확인

2026-11-15에 `simulationDebug`를 일반 Android 에뮬레이터(Pixel 7 Pro 이미지, API 33)에 띄워 확인했다. 화면 크기는 Pleos Connect의 앱 창에 가깝게 1710×1213px, 213dpi(약 1284×911dp)로 맞췄다. 차량 상태는 "모의 입력" 띠로 바꿨다. SDK 연동 검증이 아니다.

| 본 것 | 결과 |
|---|---|
| 홈: 관찰 가능, 미지원 안내, 이어 하기 배너, 준비 중인 실험 | 보임. 확인 중 표시와 권한·연결·확인 실패 안내는 따로 띄워 보지 않음 |
| 차량 경로: 준비 → 예측 → 조작 대기 → 관찰 → 설명(탐험, 이해) → 이해 확인(오답 포함) → 완료 | 끝까지 진행. 기록에 "차에서 관찰 1회" |
| 가상 경로: 모형 레버로 끝까지 | 끝까지 진행. 기록에 "가상 실험 1회". 차량 관찰 횟수는 그대로 |
| 조작 대기 중 예외: 이미 켜져 있음, 다른 쪽 신호, 알 수 없는 값, 연결 끊김과 복구 | 안내와 버튼이 사유별로 다르게 나옴 |
| 기어 D → P | 멈춤 화면이 곧바로 나오고, P로 돌아와도 "이어 하기"를 눌러야 이어짐 |
| 기어 읽기 실패 | 잠깐 "차량 상태를 확인하고 있어요"를 보인 뒤 "주차 상태를 확인할 수 없어요"로 잠김 |
| 예측이 맞았을 때와 틀렸을 때 | 내 예측과 확인한 값을 나란히 보이고 문장이 달라짐 |
| 기록 화면, 기록 지우기 | 보임. 확인 창에서 지우면 횟수가 0회로 돌아감. 날짜는 한국어 표기 |
| 창 크기: 약 1284×911dp, 826×911dp, 676×911dp, 1284×676dp | 좌우 배치와 위아래 배치가 폭에 따라 바뀌고 단계가 유지됨 |
| 한글 줄바꿈 | 어절 중간에서 끊기지 않음. 접근성 텍스트에는 원래 문장이 그대로 나옴 |

보지 못한 것: Pleos Connect 에뮬레이터에서의 실제 글자 크기와 여백, 시스템 카메라 창이 떠 있을 때의 모습, 화면 낭독기로 읽는 순서, 글자 크기를 키운 설정.

## 9. Pleos Connect 에뮬레이터 캡처에서 읽은 것

2026-11-15에 전달받은 캡처 네 장(Pleos Connect v2 API 34 에뮬레이터, 이전 버전 앱 실행 화면)에서 읽은 내용이다. 이 앱을 그 에뮬레이터에서 돌려 확인한 것이 아니다. 화면을 다듬는 근거로 썼다.

| 읽은 것 | 화면에 반영한 것 |
|---|---|
| 시스템 화면은 밝은 바탕이다. 주행 정보 영역과 상단 줄이 흰색·옅은 회색이고, 선택된 기어 칸만 검다 | 앱도 밝은 바탕, 흰 카드, 먹색 버튼을 쓴다 |
| 앱 창은 화면 전체가 아니라 주행 정보 영역 오른쪽의 약 3분의 2다. 캡처 배율로 어림하면 화면은 2560×1440px, 앱 창은 약 1710×1213px이다 | 그 크기로 맞춘 에뮬레이터에서 배치를 확인했다. 좌우 배치 기준 폭을 720dp로 낮게 잡았다 |
| 왼쪽 방향지시등을 켜면 앱 창 왼쪽 위에 사각지대 카메라 창이 뜬다. 어림으로 앱 창 폭의 약 44%, 높이의 약 35%를 덮는다 | 조작 대기와 관찰 단계에서 차량 상태, 관찰 결과, 다음 버튼을 왼쪽 아래에 둔다. 관찰 뒤에도 켜져 있으면 끄도록 알린다 |
| 카메라 창이 떠 있는 동안에도 이전 버전 앱의 화면이 관찰 결과로 바뀌어 있었다 | 6-6의 걱정(카메라 창 때문에 앱이 멈춤)이 덜할 수 있다는 근거로만 적어 둔다 |
| 기어 D에서는 시스템이 앱 창을 "주행 중에는 사용할 수 없습니다."로 가린다 | 앱의 멈춤 화면도 같은 식으로 가운데에 짧게 알린다 |

화면 밀도는 캡처만으로는 알 수 없어 213dpi로 어림했으나, 나중에 개발 에뮬레이터 규격이 2560 x 1440, 160dpi로 확인됐다. 따라서 앱 창은 약 1710×1213dp이고, 8절의 확인(약 1284×911dp)은 실제보다 작은 창에서 한 것이다. 160dpi 크기에서는 다시 보지 못했다.
