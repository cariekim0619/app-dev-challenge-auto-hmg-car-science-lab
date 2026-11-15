package com.carsciencelab.learning

import com.carsciencelab.vehicle.ObservationQuality
import com.carsciencelab.vehicle.ObservedValue

/**
 * 실험 한 번의 진행을 관리하는 상태 기계.
 *
 * READY → PREDICT → WAITING → OBSERVED → EXPLAIN → CHECK → COMPLETE
 *
 * 화면 코드에 전환 조건을 흩어 놓지 않기 위해 모든 전환을 여기서 처리한다.
 * Android와 코루틴에 의존하지 않으므로 JVM 테스트로 검증한다.
 *
 * 지키는 규칙
 * - 차량 경로에서는 과제를 시작한 뒤에 확인한 변화만 관찰로 인정한다.
 *   시작할 때 이미 목표 상태였다면 성공으로 처리하지 않는다.
 * - 알 수 없는 값은 꺼짐이나 정답으로 바꾸지 않는다.
 * - 차량 관찰은 가상 경로를, 모형 조작은 차량 경로를 진행시키지 않는다.
 * - 학습 허용 조건이 깨지면 모든 진행 판정을 멈추고, 다시 허용돼도 사용자가
 *   이어 하기를 누르기 전에는 재개하지 않는다.
 * - 완료 기록은 한 번의 실험에서 정확히 한 번만 내보낸다.
 */
class LessonEngine private constructor(
    private val spec: LessonSpec,
    private val taskMark: () -> Long,
    initial: LessonState,
) {
    var state: LessonState = initial
        private set

    fun dispatch(event: LessonEvent): List<LessonEffect> {
        val result = reduce(state, event)
        state = result.state
        return result.effects
    }

    private data class Result(val state: LessonState, val effects: List<LessonEffect> = emptyList())

    private fun reduce(s: LessonState, event: LessonEvent): Result = when (event) {
        is LessonEvent.PermissionChanged -> onPermissionChanged(s, event.allowed)
        LessonEvent.Resume -> onResume(s)
        else -> if (s.hold != SafetyHold.NONE) Result(s) else reduceActive(s, event)
    }

    private fun reduceActive(s: LessonState, event: LessonEvent): Result = when (event) {
        LessonEvent.Begin ->
            if (s.stage == LessonStage.READY) Result(s.copy(stage = LessonStage.PREDICT)) else Result(s)

        is LessonEvent.Predict -> onPredict(s, event.optionId)
        is LessonEvent.VehicleObserved -> onVehicleObserved(s, event.observation)
        is LessonEvent.LinkChanged -> onLinkChanged(s, event.connected)
        LessonEvent.RefreshVehicleState -> onRefreshVehicleState(s)
        LessonEvent.RecheckVehicle -> onRecheckVehicle(s)
        is LessonEvent.ModelActed -> onModelActed(s, event.code)

        LessonEvent.AcknowledgeObservation ->
            if (s.stage == LessonStage.OBSERVED) Result(s.copy(stage = LessonStage.EXPLAIN)) else Result(s)

        LessonEvent.FinishExplanation ->
            if (s.stage == LessonStage.EXPLAIN) Result(s.copy(stage = LessonStage.CHECK)) else Result(s)

        is LessonEvent.Answer -> onAnswer(s, event.optionId)
        LessonEvent.Restart -> Result(restarted(s))

        // 위의 reduce에서 이미 처리한다.
        is LessonEvent.PermissionChanged, LessonEvent.Resume -> Result(s)
    }

    // ---- 예측과 관찰 대기 ----

    private fun onPredict(s: LessonState, optionId: String): Result {
        if (s.stage != LessonStage.PREDICT) return Result(s)
        val code = spec.predictionOptions[optionId] ?: return Result(s)
        val waiting = freshWaiting(s)
        val next = s.copy(
            stage = LessonStage.WAITING,
            prediction = Prediction(optionId, code),
            waiting = waiting,
            modelCode = spec.restingCode,
        )
        val needsSnapshot = s.path == LearningPath.VEHICLE &&
            waiting.link == LinkState.OK &&
            waiting.hint == WaitingHint.STATE_UNKNOWN
        return Result(next, if (needsSnapshot) listOf(LessonEffect.RequestSnapshot) else emptyList())
    }

    /** 과제를 새로 시작할 때의 대기 상태. 지금 알고 있는 차량 상태를 기준으로 삼는다. */
    private fun freshWaiting(s: LessonState): WaitingState {
        val mark = taskMark()
        if (s.path == LearningPath.VIRTUAL) {
            return WaitingState(startMark = mark, armed = true, hint = WaitingHint.NONE)
        }
        val link = if (s.linkUp) LinkState.OK else LinkState.LOST
        val view = s.vehicle
        return when {
            view.validity != VehicleValidity.VALID || view.code == null ->
                WaitingState(mark, armed = false, hint = WaitingHint.STATE_UNKNOWN, link = link)

            view.code == spec.targetCode ->
                WaitingState(mark, armed = false, hint = WaitingHint.ALREADY_AT_TARGET, link = link)

            view.code == spec.restingCode ->
                WaitingState(mark, armed = true, hint = WaitingHint.NONE, link = link)

            else -> WaitingState(
                mark,
                armed = true,
                hint = WaitingHint.OTHER_VALUE_OBSERVED,
                link = link,
                otherCode = view.code,
            )
        }
    }

    private fun onVehicleObserved(s: LessonState, sequenced: SequencedObservation): Result {
        if (s.path != LearningPath.VEHICLE) return Result(s)
        val view = toView(sequenced)
        val waiting = s.waiting
        if (s.stage != LessonStage.WAITING || waiting == null || waiting.link != LinkState.OK) {
            // 과제 밖에서는 화면에 보여 줄 현재 상태만 갱신한다. 완료나 보상을 다시 실행하지 않는다.
            return Result(s.copy(vehicle = view))
        }
        val code = view.code
        if (view.validity != VehicleValidity.VALID || code == null) {
            return Result(
                s.copy(vehicle = view, waiting = waiting.copy(hint = WaitingHint.STATE_UNKNOWN, otherCode = null)),
            )
        }
        if (code == spec.targetCode) {
            val changedAfterTaskStart = waiting.armed && sequenced.isAfter(waiting.startMark)
            return if (changedAfterTaskStart) {
                Result(observedSuccess(s.copy(vehicle = view)))
            } else {
                Result(
                    s.copy(
                        vehicle = view,
                        waiting = waiting.copy(
                            armed = false,
                            hint = WaitingHint.ALREADY_AT_TARGET,
                            otherCode = null,
                        ),
                    ),
                )
            }
        }
        val isResting = code == spec.restingCode
        return Result(
            s.copy(
                vehicle = view,
                waiting = waiting.copy(
                    armed = true,
                    hint = if (isResting) WaitingHint.NONE else WaitingHint.OTHER_VALUE_OBSERVED,
                    otherCode = if (isResting) null else code,
                ),
            ),
        )
    }

    private fun toView(sequenced: SequencedObservation): VehicleView {
        val observation = sequenced.observation
        val value = observation.value
        return when {
            observation.quality == ObservationQuality.VALID && value is ObservedValue.Known ->
                VehicleView(VehicleValidity.VALID, value.code)

            observation.quality == ObservationQuality.UNAVAILABLE || value == null ->
                VehicleView(VehicleValidity.UNAVAILABLE, null)

            else -> VehicleView(VehicleValidity.UNKNOWN, null)
        }
    }

    private fun observedSuccess(s: LessonState): LessonState = s.copy(
        stage = LessonStage.OBSERVED,
        waiting = null,
        observed = ObservedResult(
            code = spec.targetCode,
            predictionMatched = s.prediction?.let { it.code == spec.targetCode },
            path = s.path,
        ),
        modelCode = spec.targetCode,
    )

    private fun onLinkChanged(s: LessonState, connected: Boolean): Result {
        if (s.path != LearningPath.VEHICLE) return Result(s)
        val waiting = s.waiting
        return if (!connected) {
            // 연결이 끊기면 현재값을 무효화하고 관찰 대기를 멈춘다.
            Result(
                s.copy(
                    linkUp = false,
                    vehicle = VehicleView.NONE,
                    waiting = waiting?.copy(
                        armed = false,
                        link = LinkState.LOST,
                        hint = WaitingHint.NONE,
                        otherCode = null,
                    ),
                ),
            )
        } else {
            // 연결이 돌아와도 예전 상태로 바로 재개하지 않는다. 사용자가 다시 확인을 눌러야 한다.
            val nextWaiting = if (waiting != null && waiting.link == LinkState.LOST) {
                waiting.copy(link = LinkState.AWAITING_RECHECK)
            } else {
                waiting
            }
            Result(s.copy(linkUp = true, waiting = nextWaiting))
        }
    }

    private fun onRefreshVehicleState(s: LessonState): Result {
        val waiting = s.waiting
        if (s.path != LearningPath.VEHICLE || s.stage != LessonStage.WAITING || waiting == null) return Result(s)
        if (waiting.link != LinkState.OK) return onRecheckVehicle(s)
        return Result(s, listOf(LessonEffect.RequestSnapshot))
    }

    private fun onRecheckVehicle(s: LessonState): Result {
        if (s.path != LearningPath.VEHICLE || s.stage != LessonStage.WAITING || s.waiting == null) return Result(s)
        val next = s.copy(
            vehicle = VehicleView.NONE,
            waiting = WaitingState(
                startMark = taskMark(),
                armed = false,
                hint = WaitingHint.STATE_UNKNOWN,
                link = LinkState.OK,
            ),
        )
        return Result(next, listOf(LessonEffect.RestartVehicleSession))
    }

    // ---- 원리 모형 ----

    private fun onModelActed(s: LessonState, code: String): Result {
        val isKnownCode = code == spec.targetCode || code == spec.restingCode ||
            code in spec.predictionOptions.values
        if (!isKnownCode) return Result(s)
        return when (s.stage) {
            LessonStage.WAITING -> {
                // 모형 조작은 가상 경로에서만 과제를 진행시킨다.
                if (s.path != LearningPath.VIRTUAL) return Result(s)
                val waiting = s.waiting ?: return Result(s)
                when (code) {
                    spec.targetCode -> Result(observedSuccess(s))
                    spec.restingCode -> Result(
                        s.copy(modelCode = code, waiting = waiting.copy(hint = WaitingHint.NONE, otherCode = null)),
                    )

                    else -> Result(
                        s.copy(
                            modelCode = code,
                            waiting = waiting.copy(hint = WaitingHint.OTHER_VALUE_OBSERVED, otherCode = code),
                        ),
                    )
                }
            }

            // 설명 단계에서는 어느 경로든 모형을 자유롭게 움직여 볼 수 있다. 진행에는 영향이 없다.
            LessonStage.EXPLAIN -> Result(s.copy(modelCode = code))
            else -> Result(s)
        }
    }

    // ---- 이해 확인 ----

    private fun onAnswer(s: LessonState, optionId: String): Result {
        if (s.stage != LessonStage.CHECK || optionId !in spec.checkOptionIds) return Result(s)
        return if (optionId == spec.correctCheckOptionId) {
            Result(
                s.copy(stage = LessonStage.COMPLETE, check = s.check.copy(lastWrongOptionId = null)),
                listOf(LessonEffect.RecordCompletion(s.lessonId, s.path)),
            )
        } else {
            Result(s.copy(check = CheckState(attempts = s.check.attempts + 1, lastWrongOptionId = optionId)))
        }
    }

    private fun restarted(s: LessonState): LessonState = LessonState(
        lessonId = s.lessonId,
        path = s.path,
        stage = LessonStage.READY,
        vehicle = s.vehicle,
        linkUp = s.linkUp,
        modelCode = spec.restingCode,
        run = s.run + 1,
    )

    // ---- 학습 허용 조건 ----

    private fun onPermissionChanged(s: LessonState, allowed: Boolean): Result {
        if (!allowed) {
            // 멈춘 동안에는 차량 값을 믿지 않는다.
            return Result(s.copy(hold = SafetyHold.BLOCKED, vehicle = VehicleView.NONE))
        }
        return when (s.hold) {
            SafetyHold.NONE, SafetyHold.AWAITING_RESUME -> Result(s)
            SafetyHold.BLOCKED -> {
                val inProgress = s.stage != LessonStage.READY && s.stage != LessonStage.COMPLETE
                if (inProgress) {
                    Result(s.copy(hold = SafetyHold.AWAITING_RESUME))
                } else {
                    Result(s.copy(hold = SafetyHold.NONE), vehicleRestartEffects(s))
                }
            }
        }
    }

    private fun onResume(s: LessonState): Result {
        if (s.hold != SafetyHold.AWAITING_RESUME) return Result(s)
        val released = s.copy(hold = SafetyHold.NONE, vehicle = VehicleView.NONE)
        val next = if (released.stage == LessonStage.WAITING) {
            // 예전 관찰 기준을 버리고 새 기준으로 다시 기다린다.
            released.copy(waiting = freshWaiting(released), modelCode = spec.restingCode)
        } else {
            released
        }
        return Result(next, vehicleRestartEffects(s))
    }

    private fun vehicleRestartEffects(s: LessonState): List<LessonEffect> =
        if (s.path == LearningPath.VEHICLE) listOf(LessonEffect.RestartVehicleSession) else emptyList()

    companion object {
        /**
         * [taskMark]는 관찰 과제를 시작할 때마다 불러 그 시점의 수신 순서 표식을 받는다.
         * 차량 경로에서는 [ObservationInterpreter.beginTask]를 넘긴다.
         */
        fun start(
            spec: LessonSpec,
            path: LearningPath,
            taskMark: () -> Long = { 0L },
        ): LessonEngine = LessonEngine(
            spec = spec,
            taskMark = taskMark,
            initial = LessonState(
                lessonId = spec.lessonId,
                path = path,
                stage = LessonStage.READY,
                modelCode = spec.restingCode,
            ),
        )

        /**
         * 관찰을 마친 뒤의 단계(설명·이해 확인)에서 저장해 둔 지점으로 이어 한다.
         * 관찰 전 단계는 차량 상태를 새로 확인해야 하므로 복원하지 않는다.
         */
        fun restore(
            spec: LessonSpec,
            path: LearningPath,
            stage: LessonStage,
            predictionMatched: Boolean?,
            taskMark: () -> Long = { 0L },
        ): LessonEngine {
            require(stage == LessonStage.EXPLAIN || stage == LessonStage.CHECK) {
                "설명 또는 이해 확인 단계만 복원할 수 있습니다: $stage"
            }
            return LessonEngine(
                spec = spec,
                taskMark = taskMark,
                initial = LessonState(
                    lessonId = spec.lessonId,
                    path = path,
                    stage = stage,
                    observed = ObservedResult(spec.targetCode, predictionMatched, path),
                    modelCode = spec.targetCode,
                ),
            )
        }
    }
}
