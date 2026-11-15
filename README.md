# 체험형 자동차 과학실

부모님 차에서 보호자와 함께 하는 어린이 자동차 과학 실험 앱이다. Pleos Connect(Android Automotive OS)에서 동작한다.
지금은 실험 하나를 처음부터 끝까지 다룬다. "방향지시등은 왜 깜빡여요?"

앱은 차량을 조작하지 않는다. 보호자가 직접 조작하고, 앱은 차량 상태를 읽어 관찰을 확인한다.

## 제출 정보

| 항목 | 내용 |
|---|---|
| 앱 이름 | 자동차 과학실 |
| 한 줄 소개 | 주차한 부모님 차에서 보호자와 아이가 방향지시등을 직접 켜 보고, 차량 상태로 관찰을 확인하며 원리를 배우는 어린이 과학 실험 앱 |
| 실행 방법 | 아래 "에뮬레이터에서 실행" |
| 테스트 환경 | Pleos Connect v2.1.0 APIs Intel x86_64 Atom System Image, 화면 2560 x 1440 dp, 160dpi. Vehicle SDK 2.3.2, JDK 17, Android SDK Platform 35 |
| 필요한 설정 | AVD는 RAM 4GB, Cold boot. 기어 P에서 실행. Virtual Console은 `adb forward tcp:8088 tcp:8088` 뒤 `http://127.0.0.1:8088/` |
| APK 위치 | `apk/성장준_자동차 과학실_release.apk` |
| 저장소 | <https://github.com/cariekim0619/app-dev-challenge-auto-hmg-car-science-lab> |

## 지금 상태 (2026-11-15 밤)

| 부분 | 상태 |
|---|---|
| 학습 엔진, 진행 제어, 콘텐츠, 기록 | 구현. Gradle에서 JVM 테스트 145개 통과(두 구성 모두) |
| 차량 연동 (방향지시등 조회, 주차·UX 제한 확인) | 구현. 실제 Pleos Vehicle SDK 2.3.2로 컴파일되고 `pleosDebug` APK가 만들어짐. Pleos Connect 에뮬레이터에서 실행하지는 못함 |
| 화면 (Compose) | 구현. `simulationDebug`를 일반 Android 에뮬레이터에 띄워 모든 단계와 예외 화면을 확인. Pleos Connect 에뮬레이터에서는 보지 못함 |
| Gradle 빌드 | 두 구성의 debug APK 빌드 확인(JDK 17, Android SDK 35) |

**이 저장소에는 APK가 들어 있지 않다.** 아래 순서대로 빌드하면 `app/build/outputs/apk/` 아래에 생긴다.
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

Windows에서는 `./gradlew` 대신 `gradlew.bat`을 쓴다. 명령줄 빌드는 `ANDROID_HOME`이 잡혀 있거나 `local.properties`에 `sdk.dir`이 있어야 한다. Android Studio로 한 번 열면 `local.properties`가 만들어진다. JDK가 여러 개라면 `JAVA_HOME`을 JDK 17로 맞춘다.

개발 빌드는 화면 아래에 "모의 입력" 띠가 붙는다. 방향지시등, 연결, 지원 확인, 기어, UX 제한을 눌러 바꿔 볼 수 있다. 줄이 길면 옆으로 민다. 앱 화면을 덮지 않으므로 누르면서 화면이 바뀌는 것을 그대로 볼 수 있다. 차량 빌드에는 이 띠가 없다.

화면은 Pleos Connect의 앱 창 크기로 보는 것이 좋다. 일반 에뮬레이터라면 아래처럼 맞춘다(끝나면 `wm size reset`, `wm density reset`).

```bash
adb shell wm size 1710x1213
adb shell wm density 160
```

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

2026-11-15에 macOS(JDK 17, Android SDK 35)에서 두 구성 모두 빌드됐다. 환경이 달라 오류가 난다면 가능성이 높은 곳부터 본다.

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
| `simulation` | 화면 개발 | 화면 아래의 "모의 입력" 띠 | 일반 Android 에뮬레이터 |

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
  ui/         진행 제어(LabController)와 Compose 화면(screen/), 원리 모형과 그림(model/), 색·글자(theme/)
app/src/pleos/        Pleos Vehicle SDK 접점(PleosVehicleLink), 주차·UX 제한(CarSafetyMonitor), 권한
app/src/simulation/   개발용 모의 차량과 조작 패널
app/src/main/assets/lessons/   실험 콘텐츠 JSON
app/src/test/         JVM 테스트
docs/                 검증 범위(verified-features.md), 시연 순서(demo.md)
```

## 화면

Pleos Connect 시스템 화면과 같은 결의 밝은 바탕을 쓴다. 옅은 회색 바탕, 흰 카드, 검은 글자, 가는 테두리다. 색과 글자 크기는 `ui/theme/Theme.kt`, 공통 요소는 `ui/screen/Components.kt`에 있다.

- **색은 정보의 출처를 구분하는 데만 쓴다.** 초록은 차량에서 확인한 사실, 파랑은 원리 모형과 가상 실험, 주황은 방향지시등의 빛과 지금 해 줄 일(먼저 끄기, 실험 뒤 끄기, 힌트), 먹색은 지금 누를 버튼이다. 차량 관찰 결과와 모형 결과를 같은 색으로 그리지 않는다.
- **실험 화면은 왼쪽에 설명, 오른쪽에 원리 모형을 둔다.** 폭이 720dp보다 좁으면 위아래로 쌓는다. 왼쪽은 읽을 것을 위에, 누르거나 확인할 것을 아래에 붙이고, 차량 상태 한 줄과 단계의 버튼을 맨 아래에 고정한다. 글이 길어 스크롤돼도 버튼은 늘 같은 자리에 있다.
- **확인할 것을 아래에 두는 데에는 이유가 하나 더 있다.** Pleos Connect는 방향지시등이 켜지면 앱 창 왼쪽 위에 사각지대 카메라 창을 띄운다. 그 순간 봐야 하는 차량 상태와 관찰 결과, 다음 버튼이 가려지지 않게 했다. 관찰이 끝난 뒤에도 장치가 켜져 있으면 끄도록 알린다.
- **단계는 이름 붙은 네 칸(예측, 관찰, 원리, 확인)으로 보여 준다.** 탐험 모드는 한 번에 한 문장과 큰 선택지 두 개만 보인다. 방향을 고르는 선택지에는 모형의 계기판 화살표와 같은 모양의 화살표를 붙였다.
- **한글은 어절 단위로 줄바꿈한다.** 화면의 글자는 모두 `ui/theme/LabText.kt`의 `LabText`로 그린다. Android는 한글을 글자 단위로 끊을 수 있어서, 어절 안에 보이지 않는 단어 결합자를 넣는다. 화면 낭독기에는 원래 문장을 알린다.
- **움직이는 것은 셋뿐이다.** 원리 모형의 깜빡임, 차량의 알림을 기다리는 점 세 개, 단계와 화면이 바뀔 때의 짧은 겹침이다. 학습을 잠글 때는 겹침 없이 곧바로 실험 화면을 내린다.

## 차량 연동에서 지키는 것

- 조회 API만 부른다. 권한도 조회용(`pleos.car.permission.CAR_LIGHTS`, `android.car.permission.CAR_POWERTRAIN`)만 선언한다.
- 기어 P와 UX 제한 없음을 모두 확인했을 때만 학습을 연다. 하나라도 읽지 못하면 잠근다. 가상 실험도 같다.
- 미지원, 권한 없음, 연결 문제, 확인 실패를 구분해 화면에 넘긴다.
- 모르는 값과 없는 값을 꺼짐으로 바꾸지 않는다.
- 과제를 시작한 뒤의 변화만 관찰로 인정한다. 이미 켜져 있던 것은 성공이 아니다.
- 실험 화면이 떠 있는 동안에만 구독하고, 벗어나면 같은 리스너로 해제한다.

logcat 태그는 `CarScienceLab.Vehicle`, `CarScienceLab.Safety`다. 차량 식별 정보는 기록하지 않는다.
