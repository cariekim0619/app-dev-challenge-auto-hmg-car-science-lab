# 시연 순서

**초안이다. 에뮬레이터에서 아직 확인하지 못했다.** 기대 화면과 로그는 코드에서 뽑았다. 처음 돌려 본 뒤 실제와 다른 곳을 고친다.
대상은 차량 빌드(`pleosDebug`)와 Pleos Connect 에뮬레이터, 공식 Virtual Console이다. 앱은 차량을 조작하지 않으므로 모든 차량 조작은 Virtual Console에서 한다.

## 1. 환경

시연 전에 실제 값을 적는다.

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect v2.1.0, API 34, RAM 4GB, Cold boot (실제 값으로 고칠 것) |
| Vehicle SDK | 앱의 "실험 기록" → "실행 환경"에 표시되는 값 |
| Vehicle Service | 같은 곳에 표시되는 값 |
| 앱 | `pleosDebug`, 0.1.0 |

## 2. 준비

1. 에뮬레이터를 Cold boot로 켠다.
2. Virtual Console을 연결한다.

   ```bash
   adb forward tcp:8088 tcp:8088
   ```

   브라우저에서 `http://127.0.0.1:8088/`을 연다.
3. Virtual Console에서 기어를 P로 둔다.
4. Virtual Console의 Convenience → Turn Signal State에서 방향지시등을 한 번 켰다가 끈다. 부팅 뒤 한 번도 조작하지 않은 방향지시등은 값이 없을 수 있다.
5. 앱을 설치하고 실행한다.

   ```bash
   ./gradlew installPleosDebug
   adb shell am start -n com.carsciencelab/.MainActivity
   adb logcat -s CarScienceLab.Vehicle CarScienceLab.Safety CarScienceLab
   ```

## 3. 순서

| 번호 | 조작 | 기대 화면 | 기대 로그 |
|---|---|---|---|
| 1 | 앱을 연다 | 홈. "방향지시등은 왜 깜빡여요?" 카드에 "이 차에서 관찰할 수 있어요" | `ux restriction state=UNRESTRICTED`, `gear initial value=4`, `vehicle link opened`, `capability turn_signal_state accessType=READ` |
| 2 | "차에서 관찰하기" | 질문과 보호자 안내, "시작하기" | `registered turn_signal_state`, `read turn_signal_state raw=OFF`. 등록 직후 `update …` 한 줄이 더 올 수 있다 |
| 3 | "시작하기" → 예측에서 "왼쪽" | "보호자와 함께 왼쪽 방향지시등을 켜 보세요.", "차가 알려 줄 때까지 기다리고 있어요." | 없음 |
| 4 | Virtual Console에서 왼쪽 방향지시등을 켠다 | "왼쪽 방향지시등 상태가 확인됐어요.", "예측한 대로예요!". 원리 모형의 왼쪽이 깜빡인다 | `update turn_signal_state raw=LEFT session=session-1` |
| 5 | "왜 그런지 알아보기" | 설명이 한 문장씩 나온다. "다음"으로 넘긴다 | 없음 |
| 6 | 위쪽에서 "이해" 모드로 바꾼다 | 같은 단계에 머물고 설명이 "왜 이런가", "언제 쓰는가", "더 알아보기"로 바뀐다 | 없음 |
| 7 | "이해 확인하기" → "왼쪽으로 방향을 바꾸겠다는 신호" | "실험 완료!", "차에서 직접 관찰한 실험으로 기록했어요". 방향지시등이 켜져 있으면 꺼 달라는 안내가 함께 나온다 | 없음 |
| 8 | Virtual Console에서 방향지시등을 끄고, 앱에서 "홈으로" | 홈 카드에 "차에서 관찰 1회" | `update turn_signal_state raw=OFF …`, `unregistered turn_signal_state` |

탐험 모드로 끝까지 가면 7번의 정답 문장은 "왼쪽으로 가려고 해요"다.

## 4. 차량 환경 대응을 보여 줄 때

| 장면 | 조작 | 기대 화면 |
|---|---|---|
| 이미 켜진 채로 시작 | 왼쪽 방향지시등을 켜 둔 채 실험을 열고 예측까지 한다 | "지금 왼쪽 방향지시등이 켜져 있어요. 먼저 꺼 볼까요?" 바로 성공하지 않는다. 끄고 다시 켜면 관찰로 넘어간다 |
| 주행 제한 | 3번의 대기 화면에서 기어를 D로 바꾼다 | 시스템이 화면을 가리거나 앱이 "지금은 실험을 쉬어요"를 보인다 |
| 다시 주차 | 기어를 P로 돌린다 | 자동으로 이어지지 않는다. "실험을 잠시 멈췄어요"와 "이어 하기"가 보인다 |
| 다른 쪽 신호 | 대기 화면에서 오른쪽 방향지시등을 켠다 | "오른쪽 방향지시등이 확인됐어요. 이번에는 왼쪽을 켜 볼까요?" |

미지원, 권한 없음, 연결 문제의 화면은 에뮬레이터에서 일부러 만들기 어렵다. 이 장면은 개발 빌드(`simulationDebug`)의 "모의 입력" 패널로 보여 주고, 실제 차량 연동이 아니라는 점을 밝힌다.

## 5. 복구 절차

시연 중에 막히면 이 순서로 한다.

1. 관찰로 넘어가지 않으면 화면의 "다시 확인"을 누른다. 그래도 안 되면 Virtual Console에서 방향지시등을 끄고 다시 켠다.
2. 앱이 잠겨 있으면 Virtual Console에서 기어가 P인지 본다.
3. 앱을 다시 실행한다.

   ```bash
   adb shell am force-stop com.carsciencelab
   adb shell am start -n com.carsciencelab/.MainActivity
   ```

4. 홈에서 현재 상태를 다시 확인한 뒤 같은 실험을 연다. 조작 대기 단계는 저장하지 않으므로 처음부터 시작한다. 설명이나 이해 확인 단계에서 끊겼다면 홈의 "이어 하기"로 그 단계부터 계속한다.

원인을 볼 때는 `docs/verified-features.md` 6절의 순서를 따른다.
