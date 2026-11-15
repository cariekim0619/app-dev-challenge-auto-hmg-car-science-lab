# 체험형 자동차 과학실

부모님 차에서 보호자와 함께 하는 어린이 자동차 과학 실험 앱이다. Pleos Connect(Android Automotive OS)에서 동작한다.
지금은 실험 하나를 처음부터 끝까지 다룬다. "방향지시등은 왜 깜빡여요?"

앱은 차량을 조작하지 않는다. 보호자가 직접 조작하고, 앱은 차량 상태를 읽어 관찰을 확인한다.

## 지금 상태 (2026-11-15)

| 부분 | 상태 |
|---|---|
| 학습 엔진, 진행 제어, 콘텐츠, 기록 | 구현, JVM 테스트 통과 |
| 차량 연동 (방향지시등 조회, 주차·UX 제한 확인) | 구현, 규칙은 JVM 테스트 통과. 실제 SDK로는 빌드하지 못함 |
| 화면 (Compose) | 코드 작성, 호출부를 API 서명과 대조. 기기에서 본 적 없음 |
| Gradle 빌드, 에뮬레이터 실행 | 확인하지 못함 |

**이 저장소에는 APK가 들어 있지 않다.** 작업 환경에서 Android 빌드를 돌릴 수 없었다. 처음 실행하려면 아래 순서대로 한 번 빌드해야 하고, 그때 컴파일 오류가 날 수 있다.
무엇을 어떻게 확인했고 무엇이 남았는지는 [docs/verified-features.md](docs/verified-features.md)에 있다.

## 에뮬레이터에서 실행

준비물은 Android Studio(JDK 17 이상 포함), Android SDK Platform 35, 인터넷 연결(첫 빌드 때 의존성을 내려받는다)이다.
차량 빌드를 돌리려면 Pleos Connect 에뮬레이터(v2.1.0, API 34, RAM 4GB, Cold boot)가 있어야 한다.

### Android Studio로

1. 압축을 풀고 Android Studio에서 `car-science-lab` 폴더를 연다.
2. Gradle Sync가 끝나기를 기다린다. SDK Platform 35를 설치하라고 하면 설치한다.
3. Build Variants 창에서 `app` 모듈의 변형을 고른다.
   - `pleosDebug`: Pleos Connect 에뮬레이터용 차량 빌드
   - `simulationDebug`: 일반 Android 에뮬레이터용 개발 빌드(화면과 흐름만 볼 때)
4. 실행할 에뮬레이터를 고르고 Run을 누른다.

### 명령줄로

```bash
# 차량 빌드 (Pleos Connect 에뮬레이터)
./gradlew installPleosDebug
adb shell am start -n com.carsciencelab/.MainActivity

# 개발 빌드 (일반 Android 에뮬레이터)
./gradlew installSimulationDebug
adb shell am start -n com.carsciencelab.simulation/com.carsciencelab.MainActivity
```

Windows에서는 `./gradlew` 대신 `gradlew.bat`을 쓴다. 명령줄 빌드는 `ANDROID_HOME`이 잡혀 있거나 `local.properties`에 `sdk.dir`이 있어야 한다. Android Studio로 한 번 열면 `local.properties`가 만들어진다.

### 차량 빌드를 처음 돌릴 때

1. Virtual Console을 연결한다. `adb forward tcp:8088 tcp:8088` 뒤 브라우저에서 `http://127.0.0.1:8088/`을 연다.
2. 기어가 P인지 확인한다. P가 아니면 앱이 잠긴다.
3. Virtual Console에서 방향지시등을 한 번 켰다가 끈다. 부팅 뒤 한 번도 조작하지 않은 방향지시등은 값이 없을 수 있다. 그 상태에서는 처음 켠 것을 변화로 인정하지 않고 "지금 왼쪽 방향지시등이 켜져 있어요. 먼저 꺼 볼까요?"라고 안내한다. 그때는 껐다가 다시 켜면 된다.
4. 앱에서 "차에서 관찰하기" → "시작하기" → 예측을 고른 뒤, Virtual Console에서 왼쪽 방향지시등을 켠다.
5. 로그를 본다.

```bash
adb logcat -s CarScienceLab.Vehicle CarScienceLab.Safety CarScienceLab
```

자세한 순서와 기대 로그는 [docs/demo.md](docs/demo.md), 잠기거나 관찰이 안 될 때의 점검은 [docs/verified-features.md](docs/verified-features.md) 6절에 있다.

### 빌드가 실패하면

실제 SDK로 컴파일한 적이 없으므로 첫 빌드에서 오류가 날 수 있다. 가능성이 높은 곳부터 적는다.

| 오류가 나는 곳 | 볼 것 |
|---|---|
| `app/src/pleos/…/vehicle/PleosVehicleLink.kt` | Pleos Vehicle SDK 호출 네 개. `docs/verified-features.md` 4절의 서명 표와 실제 선언을 대조한다 |
| `app/src/pleos/…/policy/CarSafetyMonitor.kt` | AAOS Car API. `app/build.gradle.kts`의 `useLibrary("android.car")`가 적용됐는지 본다 |
| `app/src/main/…/ui/` 아래 | Compose 호출. 오류 메시지의 인자 이름과 타입대로 고친다 |
| 의존성을 받지 못함 | `settings.gradle.kts`의 Pleos Nexus 주소와 네트워크를 확인한다 |

`simulationDebug`는 Pleos SDK와 Car API를 쓰지 않는다. 차량 빌드가 막히면 이 변형부터 돌려 화면을 확인할 수 있다.

설치할 때 서명이 다르다는 오류가 나면 같은 applicationId의 이전 앱을 지운다. `adb uninstall com.carsciencelab`

## 빌드와 테스트

JDK 17, Android SDK 35가 필요하다. Vehicle SDK는 Pleos Nexus 저장소에서 받는다(`settings.gradle.kts`).

```bash
./gradlew testSimulationDebugUnitTest testPleosDebugUnitTest
./gradlew assemblePleosDebug assembleSimulationDebug
```

APK는 `app/build/outputs/apk/pleos/debug/`와 `app/build/outputs/apk/simulation/debug/`에 생긴다.
GitHub에 올리면 `.github/workflows/android.yml`이 같은 명령을 실행하고 APK를 artifact로 남긴다.

## 구성

| 구성 | 용도 | 차량 상태 | 실행 환경 |
|---|---|---|---|
| `pleos` | 차량 빌드 | Pleos Vehicle SDK, AAOS Car API | Pleos Connect 에뮬레이터, 실차 |
| `simulation` | 화면 개발 | 앱 안의 "모의 입력" 패널 | 일반 Android 에뮬레이터 |

`simulation`은 debug로만 만든다. `pleos`에는 신호를 조작하는 버튼이 없고, 시연은 Virtual Console로 한다.
두 구성은 applicationId가 달라(`com.carsciencelab`, `com.carsciencelab.simulation`) 한 기기에 함께 설치할 수 있다.

## 코드

```
app/src/main/java/com/carsciencelab/
  learning/   학습 단계와 관찰 판정 (LessonEngine, ObservationInterpreter)
  vehicle/    차량 상태 읽기 계약과 규칙 (VehicleGateway, SdkVehicleGateway, VehicleLink)
  policy/     학습 허용 조건 (주차, UX 제한, 화면 표시 여부)
  content/    실험 콘텐츠 형식과 검증
  data/       완료 기록과 이어 하기 지점
  ui/         진행 제어(LabController)와 Compose 화면
app/src/pleos/        Pleos Vehicle SDK 접점(PleosVehicleLink), 주차·UX 제한(CarSafetyMonitor), 권한
app/src/simulation/   개발용 모의 차량과 조작 패널
app/src/main/assets/lessons/   실험 콘텐츠 JSON
app/src/test/         JVM 테스트
docs/                 검증 범위(verified-features.md), 시연 순서(demo.md)
```

## 차량 연동에서 지키는 것

- 조회 API만 부른다. 권한도 조회용(`pleos.car.permission.CAR_LIGHTS`, `android.car.permission.CAR_POWERTRAIN`)만 선언한다.
- 기어 P와 UX 제한 없음을 모두 확인했을 때만 학습을 연다. 하나라도 읽지 못하면 잠근다. 가상 실험도 같다.
- 미지원, 권한 없음, 연결 문제, 확인 실패를 구분해 화면에 넘긴다.
- 모르는 값과 없는 값을 꺼짐으로 바꾸지 않는다.
- 과제를 시작한 뒤의 변화만 관찰로 인정한다. 이미 켜져 있던 것은 성공이 아니다.
- 실험 화면이 떠 있는 동안에만 구독하고, 벗어나면 같은 리스너로 해제한다.

logcat 태그는 `CarScienceLab.Vehicle`, `CarScienceLab.Safety`다. 차량 식별 정보는 기록하지 않는다.
