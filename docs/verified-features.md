# 확인한 것과 확인하지 못한 것

2026-11-15 기준, 차량 연동 구현까지의 상태다. 화면의 모양과 사용성은 이 문서의 범위가 아니다. 시연 순서는 [demo.md](demo.md)에 초안이 있다.

## 1. 요약

| 구분 | 상태 |
|---|---|
| 학습 엔진, 진행 제어, 차량 게이트웨이 규칙 | JVM 테스트로 확인 |
| SDK를 직접 부르는 코드(`PleosVehicleLink`, `CarSafetyMonitor`) | 실제 SDK로 컴파일하지 못함. 호출 형태를 공개 자료와 대조 |
| Gradle 빌드 | 한 번도 실행하지 못함 |
| Pleos Connect 에뮬레이터, 실차 동작 | 확인하지 못함 |

작업 환경에서 Google Maven과 Pleos Nexus에 접근할 수 없어 Android 빌드를 돌리지 못했다.
첫 빌드는 Android Studio나 `.github/workflows/android.yml`로 해야 한다.

## 2. 확인한 것

| 방법 | 대상 | 결과 |
|---|---|---|
| JVM 단위 테스트 145개 (kotlinc 2.0.0, JUnit 4.13.2) | 학습 엔진, 관찰 해석, 진행 제어, 콘텐츠, 기록, 학습 허용 정책, 오류 분류, 차량 게이트웨이, 차량 실험 흐름 | 모두 통과 |
| 변형 검사 24개 | 차량 게이트웨이와 오류 분류의 규칙을 하나씩 깨뜨림 | 모두 테스트가 잡음 |
| 변형 검사 18개 (학습 엔진 초안 단계에 실행) | 학습 엔진, 관찰 해석 | 모두 테스트가 잡음 |
| 대역 컴파일 | Android, Compose, Pleos SDK를 쓰는 코드 전체. `pleos`, `simulation` 두 구성 | 통과 |
| 호출부 대조 | 화면과 Android 쪽 파일(`ui/`, `MainActivity`, `LabViewModel`, DataStore 저장소, `simulation`의 조작 패널)의 Compose·AndroidX 호출을 하나씩 읽어 API 서명과 대조. 실험적 API(opt-in 필요)는 쓰지 않음 | 고칠 곳을 찾지 못함 |
| Compose 문맥 규칙 | `@Composable` 호출이 `@Composable` 문맥 안에만 있는지. `@Composable`을 `suspend`로 바꿔 Kotlin 컴파일러의 suspend 호출 규칙으로 대신 검사(두 구성). 일부러 넣은 위반 1건이 잡히는 것도 확인 | 통과 |
| 리소스 참조 | 리소스 XML과 Manifest의 `@종류/이름` 참조, 문자열의 형식 지정자와 이스케이프 | 풀리지 않는 참조 없음 |

테스트는 Gradle이 아니라 컴파일러와 JUnit을 직접 불러 실행했다. Gradle에서 같은 테스트가 도는지는 확인하지 못했다.

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

1. **빌드.** `./gradlew assemblePleosDebug`. 컴파일 오류가 난다면 SDK를 직접 부르는 `PleosVehicleLink.kt`와 `CarSafetyMonitor.kt`일 가능성이 가장 높다.
2. **잠금 해제.** 기어 P에서 앱을 열었을 때 홈 화면이 나오는지 본다. 잠겨 있으면 `gear initial value=`와 `ux restriction state=` 로그를 본다. 기어를 읽지 못하면 앱은 계속 잠긴다.
3. **지원 확인.** 홈 카드가 차량 관찰 가능으로 바뀌고 `capability turn_signal_state accessType=READ`(또는 `READ_WRITE`)가 찍히는지 본다.
4. **현재값 조회.** 실험을 열면 `read turn_signal_state raw=OFF`가 찍히는지 본다. `raw=null`이거나 `read failed … code=ERROR_EMPTY_RESULT`이면 값이 아직 없는 상태다. 이때는 처음 켠 왼쪽 방향지시등을 "이미 켜져 있어요"로 안내하고, 끄고 다시 켜야 관찰로 인정한다. 시연 전에 방향지시등을 한 번 켰다 끄면 이 경로를 피할 수 있다.
5. **변화 알림.** Virtual Console에서 왼쪽을 켜면 `update turn_signal_state raw=LEFT`가 찍히고 관찰 단계로 넘어가는지 본다.
6. **사각지대 카메라 창.** 방향지시등을 켰을 때 뜨는 시스템 창 때문에 앱이 멈춤 화면으로 바뀌는지 본다. 앱은 `onStop`을 받으면 학습을 멈추고 구독을 해제한다. 작은 창이라 `onStop`까지 가지 않을 것으로 보지만 확인하지 못했다.
7. **주행 제한.** 기어를 D로 바꾸면 화면이 가려지고, P로 돌아오면 자동으로 이어지지 않고 "이어 하기"가 나오는지 본다.
8. **해제.** 실험에서 나가면 `unregistered turn_signal_state`가 찍히는지 본다.

## 7. 알려진 한계

- 차량 서비스가 답하지 않을 때의 처리(시간 제한 5초, 다음 요청 때 다시 연결)는 가짜 SDK 접점으로만 시험했다. 실제 SDK가 `release()` 뒤 다시 `initialize()`하는 것을 허용하는지는 확인하지 못했다.
- `pleos.car.permission.CAR_LIGHTS`가 설치할 때 부여되는지 에뮬레이터에서 확인하지 못했다. 공식 Sample App의 Manifest 주석은 Vehicle Service의 조회 권한이 `protectionLevel="normal"`이라고 적고 있고, Sample App도 선언만 하고 실행 중 요청을 하지 않는다. 이 앱도 선언만 한다.
- 차량 빌드는 차량용 기기(Pleos Connect 에뮬레이터, 실차)에서만 쓴다. 차량용이 아닌 기기에서는 차량 라이브러리를 건드리지 않고 잠긴 채로 두도록 만들었지만(`automotive feature missing`), 그 기기에서 Pleos SDK가 어떻게 동작하는지는 확인하지 못했다. 화면만 보려면 `simulation` 구성을 쓴다.
- 실차에서는 어떤 것도 확인하지 못했다.
