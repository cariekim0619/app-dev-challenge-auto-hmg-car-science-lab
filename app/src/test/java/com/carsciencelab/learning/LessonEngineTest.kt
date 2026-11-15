package com.carsciencelab.learning

import com.carsciencelab.vehicle.Observation
import com.carsciencelab.vehicle.ObservationKind
import com.carsciencelab.vehicle.ObservationSource
import com.carsciencelab.vehicle.ObservedValue
import com.carsciencelab.vehicle.SignalKey
import com.carsciencelab.vehicle.TurnSignalCodes.LEFT_ACTIVE
import com.carsciencelab.vehicle.TurnSignalCodes.OFF
import com.carsciencelab.vehicle.TurnSignalCodes.RIGHT_ACTIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LessonEngineTest {

    private val spec = LessonSpec(
        lessonId = "turn_signal",
        targetCode = LEFT_ACTIVE,
        restingCode = OFF,
        predictionOptions = mapOf("left" to LEFT_ACTIVE, "right" to RIGHT_ACTIVE),
        checkOptionIds = setOf("direction", "hurry"),
        correctCheckOptionId = "direction",
    )

    /** 엔진과 관찰 해석을 실제 앱과 같은 순서로 묶은 테스트 도구. */
    private inner class Lab(path: LearningPath) {
        val interpreter = ObservationInterpreter()
        val engine = LessonEngine.start(spec, path, interpreter::beginTask)
        val effects = mutableListOf<LessonEffect>()
        var sessionId = "s1"

        init {
            interpreter.startSession(sessionId)
        }

        val state: LessonState get() = engine.state

        fun send(event: LessonEvent) {
            effects += engine.dispatch(event)
        }

        fun deliver(observation: Observation, requestMark: Long? = null): Interpretation {
            val result = interpreter.accept(observation, requestMark)
            if (result is Interpretation.Accepted) send(LessonEvent.VehicleObserved(result.observation))
            return result
        }

        /** 상태 변화 알림. */
        fun update(code: String, session: String = sessionId) =
            deliver(observation(ObservedValue.Known(code), ObservationKind.UPDATE, session))

        fun updateWith(value: ObservedValue?, session: String = sessionId) =
            deliver(observation(value, ObservationKind.UPDATE, session))

        /** 지금 요청해서 바로 받은 조회 응답. */
        fun snapshot(code: String) = snapshotRequestedAt(interpreter.mark(), code)

        /** [requestMark] 시점에 요청했던 조회의 응답. */
        fun snapshotRequestedAt(requestMark: Long, code: String) =
            deliver(observation(ObservedValue.Known(code), ObservationKind.SNAPSHOT, sessionId), requestMark)

        fun completions() = effects.filterIsInstance<LessonEffect.RecordCompletion>()

        fun toWaiting(option: String = "left") {
            send(LessonEvent.Begin)
            send(LessonEvent.Predict(option))
        }

        fun fromObservedToComplete() {
            send(LessonEvent.AcknowledgeObservation)
            send(LessonEvent.FinishExplanation)
            send(LessonEvent.Answer("direction"))
        }
    }

    private fun observation(value: ObservedValue?, kind: ObservationKind, session: String) = Observation.of(
        signal = SignalKey.TURN_SIGNAL_STATE,
        value = value,
        receivedAtElapsedMs = 0L,
        source = ObservationSource.SIMULATION,
        kind = kind,
        sessionId = session,
    )

    // ---- 차량 경로: 기본 흐름 ----

    @Test
    fun `차량 경로는 과제 시작 뒤 확인한 변화로 끝까지 진행한다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        assertEquals(LessonStage.READY, lab.state.stage)

        lab.send(LessonEvent.Begin)
        assertEquals(LessonStage.PREDICT, lab.state.stage)

        lab.send(LessonEvent.Predict("left"))
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertTrue(lab.state.waiting!!.armed)
        assertEquals(WaitingHint.NONE, lab.state.waiting!!.hint)

        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
        assertEquals(ObservedResult(LEFT_ACTIVE, true, LearningPath.VEHICLE), lab.state.observed)
        assertNull(lab.state.waiting)

        lab.send(LessonEvent.AcknowledgeObservation)
        assertEquals(LessonStage.EXPLAIN, lab.state.stage)
        lab.send(LessonEvent.FinishExplanation)
        assertEquals(LessonStage.CHECK, lab.state.stage)
        lab.send(LessonEvent.Answer("direction"))
        assertEquals(LessonStage.COMPLETE, lab.state.stage)

        assertEquals(
            listOf(LessonEffect.RecordCompletion("turn_signal", LearningPath.VEHICLE)),
            lab.completions(),
        )
    }

    @Test
    fun `예측이 틀려도 관찰은 인정하고 예측과 달랐다고 기록한다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting(option = "right")

        lab.update(LEFT_ACTIVE)

        assertEquals(LessonStage.OBSERVED, lab.state.stage)
        assertEquals(false, lab.state.observed!!.predictionMatched)
    }

    // ---- 차량 경로: 시작 상태 ----

    @Test
    fun `시작할 때 이미 목표 상태면 성공으로 처리하지 않는다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(LEFT_ACTIVE)
        lab.toWaiting()

        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertFalse(lab.state.waiting!!.armed)
        assertEquals(WaitingHint.ALREADY_AT_TARGET, lab.state.waiting!!.hint)

        // 같은 값이 다시 와도 변화가 아니다.
        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertEquals(WaitingHint.ALREADY_AT_TARGET, lab.state.waiting!!.hint)

        // 끈 뒤 다시 켜면 인정한다.
        lab.update(OFF)
        assertTrue(lab.state.waiting!!.armed)
        assertEquals(WaitingHint.NONE, lab.state.waiting!!.hint)
        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
    }

    @Test
    fun `시작 상태를 모르면 현재값 조회를 요청하고 꺼짐으로 가정하지 않는다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.toWaiting()

        assertEquals(WaitingHint.STATE_UNKNOWN, lab.state.waiting!!.hint)
        assertFalse(lab.state.waiting!!.armed)
        assertEquals(listOf<LessonEffect>(LessonEffect.RequestSnapshot), lab.effects)

        // 기준을 모르는 상태에서 목표 값을 받으면 변화인지 알 수 없다.
        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertEquals(WaitingHint.ALREADY_AT_TARGET, lab.state.waiting!!.hint)

        lab.update(OFF)
        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
    }

    @Test
    fun `과제 시작 뒤에 받은 조회 응답으로 기준을 세운다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.toWaiting()

        lab.snapshot(OFF)
        assertTrue(lab.state.waiting!!.armed)
        assertEquals(WaitingHint.NONE, lab.state.waiting!!.hint)

        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
    }

    @Test
    fun `다른 쪽 신호가 확인되면 성공이 아니라 안내를 준다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()

        lab.update(RIGHT_ACTIVE)
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertEquals(WaitingHint.OTHER_VALUE_OBSERVED, lab.state.waiting!!.hint)
        assertEquals(RIGHT_ACTIVE, lab.state.waiting!!.otherCode)

        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
    }

    @Test
    fun `시작할 때 다른 쪽 신호가 켜져 있으면 그 사실을 먼저 알린다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(RIGHT_ACTIVE)
        lab.toWaiting()

        assertEquals(WaitingHint.OTHER_VALUE_OBSERVED, lab.state.waiting!!.hint)
        assertEquals(RIGHT_ACTIVE, lab.state.waiting!!.otherCode)
        assertTrue(lab.state.waiting!!.armed)
    }

    // ---- 차량 경로: 알 수 없는 값 ----

    @Test
    fun `알 수 없는 값과 없는 값을 꺼짐이나 정답으로 바꾸지 않는다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()

        lab.updateWith(ObservedValue.Unknown("BOTH"))
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertEquals(VehicleView(VehicleValidity.UNKNOWN, null), lab.state.vehicle)
        assertEquals(WaitingHint.STATE_UNKNOWN, lab.state.waiting!!.hint)

        lab.updateWith(null)
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertEquals(VehicleView(VehicleValidity.UNAVAILABLE, null), lab.state.vehicle)
        assertEquals(WaitingHint.STATE_UNKNOWN, lab.state.waiting!!.hint)
        assertTrue(lab.completions().isEmpty())
    }

    // ---- 차량 경로: 중복과 순서 ----

    @Test
    fun `같은 알림이 반복돼도 완료 기록은 한 번만 나온다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()

        lab.update(LEFT_ACTIVE)
        lab.update(LEFT_ACTIVE)
        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, lab.state.stage)

        lab.fromObservedToComplete()
        lab.update(LEFT_ACTIVE)
        lab.send(LessonEvent.Answer("direction"))
        lab.send(LessonEvent.Answer("direction"))

        assertEquals(LessonStage.COMPLETE, lab.state.stage)
        assertEquals(1, lab.completions().size)
    }

    @Test
    fun `이전 구독의 늦은 알림은 과제를 진행시키지 않는다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()

        // 구독을 새 세션으로 다시 시작한 뒤, 예전 세션의 알림이 늦게 도착한다.
        lab.sessionId = "s2"
        lab.interpreter.startSession("s2")
        val result = lab.update(LEFT_ACTIVE, session = "s1")

        assertEquals(Interpretation.Dropped(DropReason.SESSION_MISMATCH), result)
        assertEquals(LessonStage.WAITING, lab.state.stage)
    }

    @Test
    fun `늦게 도착한 조회 응답이 더 새로운 알림을 덮어쓰지 않는다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.toWaiting()
        val requestMark = lab.interpreter.mark()

        lab.update(LEFT_ACTIVE)
        lab.update(OFF)
        val result = lab.snapshotRequestedAt(requestMark, LEFT_ACTIVE)

        assertEquals(Interpretation.Dropped(DropReason.STALE_SNAPSHOT), result)
        assertEquals(VehicleView(VehicleValidity.VALID, OFF), lab.state.vehicle)
        assertTrue(lab.state.waiting!!.armed)
        assertEquals(LessonStage.WAITING, lab.state.stage)
    }

    @Test
    fun `과제 시작 전에 요청한 조회 응답으로는 성공하지 않는다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.update(OFF)
        val requestMark = lab.interpreter.mark()
        lab.toWaiting()
        assertTrue(lab.state.waiting!!.armed)

        val result = lab.snapshotRequestedAt(requestMark, LEFT_ACTIVE)

        assertTrue(result is Interpretation.Accepted)
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertFalse(lab.state.waiting!!.armed)
        assertEquals(WaitingHint.ALREADY_AT_TARGET, lab.state.waiting!!.hint)
    }

    @Test
    fun `과제 시작 뒤에 다시 읽은 현재값으로 변화를 확인할 수 있다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()
        lab.effects.clear()

        lab.send(LessonEvent.RefreshVehicleState)
        assertEquals(listOf<LessonEffect>(LessonEffect.RequestSnapshot), lab.effects)

        lab.snapshot(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
    }

    // ---- 차량 경로: 연결 ----

    @Test
    fun `연결이 끊기면 대기를 멈추고 복구 뒤에도 다시 확인을 눌러야 재개한다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()
        lab.effects.clear()

        lab.send(LessonEvent.LinkChanged(connected = false))
        assertEquals(LinkState.LOST, lab.state.waiting!!.link)
        assertEquals(VehicleView.NONE, lab.state.vehicle)

        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.WAITING, lab.state.stage)

        lab.send(LessonEvent.LinkChanged(connected = true))
        assertEquals(LinkState.AWAITING_RECHECK, lab.state.waiting!!.link)
        lab.update(OFF)
        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.WAITING, lab.state.stage)

        lab.send(LessonEvent.RecheckVehicle)
        assertEquals(listOf<LessonEffect>(LessonEffect.RestartVehicleSession), lab.effects)
        assertEquals(LinkState.OK, lab.state.waiting!!.link)
        assertEquals(WaitingHint.STATE_UNKNOWN, lab.state.waiting!!.hint)
        assertFalse(lab.state.waiting!!.armed)

        lab.snapshot(OFF)
        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
    }

    @Test
    fun `연결이 끊긴 채로 과제를 시작하면 기다리지 않는다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.send(LessonEvent.Begin)
        lab.send(LessonEvent.LinkChanged(connected = false))
        lab.effects.clear()

        lab.send(LessonEvent.Predict("left"))

        assertEquals(LinkState.LOST, lab.state.waiting!!.link)
        assertTrue(lab.effects.isEmpty())
    }

    // ---- 학습 허용 조건 ----

    @Test
    fun `제한이 걸리면 진행 판정을 멈추고 풀려도 이어 하기 전에는 재개하지 않는다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()
        lab.effects.clear()

        lab.send(LessonEvent.PermissionChanged(allowed = false))
        assertEquals(SafetyHold.BLOCKED, lab.state.hold)
        assertEquals(VehicleView.NONE, lab.state.vehicle)

        lab.update(LEFT_ACTIVE)
        lab.send(LessonEvent.Answer("direction"))
        assertEquals(LessonStage.WAITING, lab.state.stage)

        lab.send(LessonEvent.PermissionChanged(allowed = true))
        assertEquals(SafetyHold.AWAITING_RESUME, lab.state.hold)
        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertTrue(lab.effects.isEmpty())

        lab.send(LessonEvent.Resume)
        assertEquals(SafetyHold.NONE, lab.state.hold)
        assertEquals(listOf<LessonEffect>(LessonEffect.RestartVehicleSession), lab.effects)
        assertFalse(lab.state.waiting!!.armed)
        assertEquals(WaitingHint.STATE_UNKNOWN, lab.state.waiting!!.hint)

        // 멈춘 동안 켜 둔 신호는 새 기준에서 이미 목표 상태다.
        lab.snapshot(LEFT_ACTIVE)
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertEquals(WaitingHint.ALREADY_AT_TARGET, lab.state.waiting!!.hint)

        lab.update(OFF)
        lab.update(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
    }

    @Test
    fun `설명 단계에서 제한이 걸려도 단계를 보존하고 이어 하기를 기다린다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()
        lab.update(LEFT_ACTIVE)
        lab.send(LessonEvent.AcknowledgeObservation)

        lab.send(LessonEvent.PermissionChanged(allowed = false))
        lab.send(LessonEvent.FinishExplanation)
        assertEquals(LessonStage.EXPLAIN, lab.state.stage)

        lab.send(LessonEvent.PermissionChanged(allowed = true))
        lab.send(LessonEvent.FinishExplanation)
        assertEquals(LessonStage.EXPLAIN, lab.state.stage)

        lab.send(LessonEvent.Resume)
        lab.send(LessonEvent.FinishExplanation)
        assertEquals(LessonStage.CHECK, lab.state.stage)
    }

    @Test
    fun `진행 중이 아닐 때 걸린 제한은 풀리면 바로 해제된다`() {
        val vehicle = Lab(LearningPath.VEHICLE)
        vehicle.send(LessonEvent.PermissionChanged(allowed = false))
        vehicle.send(LessonEvent.Begin)
        assertEquals(LessonStage.READY, vehicle.state.stage)
        vehicle.send(LessonEvent.PermissionChanged(allowed = true))
        assertEquals(SafetyHold.NONE, vehicle.state.hold)
        assertEquals(listOf<LessonEffect>(LessonEffect.RestartVehicleSession), vehicle.effects)

        val virtual = Lab(LearningPath.VIRTUAL)
        virtual.send(LessonEvent.PermissionChanged(allowed = false))
        virtual.send(LessonEvent.PermissionChanged(allowed = true))
        assertEquals(SafetyHold.NONE, virtual.state.hold)
        assertTrue(virtual.effects.isEmpty())
    }

    @Test
    fun `이어 하기는 제한이 풀린 뒤에만 동작한다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()

        lab.send(LessonEvent.PermissionChanged(allowed = false))
        lab.send(LessonEvent.Resume)

        assertEquals(SafetyHold.BLOCKED, lab.state.hold)
    }

    // ---- 이해 확인 ----

    @Test
    fun `오답이면 완료하지 않고 다시 고를 수 있다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()
        lab.update(LEFT_ACTIVE)
        lab.send(LessonEvent.AcknowledgeObservation)
        lab.send(LessonEvent.FinishExplanation)

        lab.send(LessonEvent.Answer("hurry"))
        assertEquals(LessonStage.CHECK, lab.state.stage)
        assertEquals(CheckState(attempts = 1, lastWrongOptionId = "hurry"), lab.state.check)
        assertTrue(lab.completions().isEmpty())

        lab.send(LessonEvent.Answer("없는 선택지"))
        assertEquals(1, lab.state.check.attempts)

        lab.send(LessonEvent.Answer("direction"))
        assertEquals(LessonStage.COMPLETE, lab.state.stage)
        assertNull(lab.state.check.lastWrongOptionId)
        assertEquals(1, lab.completions().size)
    }

    @Test
    fun `단계에 맞지 않는 입력은 무시한다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)

        lab.send(LessonEvent.Predict("left"))
        lab.send(LessonEvent.Answer("direction"))
        lab.send(LessonEvent.AcknowledgeObservation)
        lab.send(LessonEvent.FinishExplanation)
        assertEquals(LessonStage.READY, lab.state.stage)

        lab.send(LessonEvent.Begin)
        lab.send(LessonEvent.Predict("없는 선택지"))
        assertEquals(LessonStage.PREDICT, lab.state.stage)

        lab.send(LessonEvent.Predict("left"))
        lab.send(LessonEvent.AcknowledgeObservation)
        lab.send(LessonEvent.FinishExplanation)
        lab.send(LessonEvent.Answer("direction"))
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertTrue(lab.completions().isEmpty())
    }

    @Test
    fun `다시 해보기는 새 실험으로 시작하고 완료하면 한 번 더 기록한다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()
        lab.update(LEFT_ACTIVE)
        lab.fromObservedToComplete()
        lab.update(OFF)

        lab.send(LessonEvent.Restart)
        assertEquals(LessonStage.READY, lab.state.stage)
        assertEquals(2, lab.state.run)
        assertNull(lab.state.prediction)
        assertNull(lab.state.observed)
        assertEquals(VehicleView(VehicleValidity.VALID, OFF), lab.state.vehicle)

        lab.toWaiting()
        lab.update(LEFT_ACTIVE)
        lab.fromObservedToComplete()
        assertEquals(2, lab.completions().size)
    }

    // ---- 가상 경로와 원리 모형 ----

    @Test
    fun `가상 경로는 모형 조작만으로 진행하고 가상 완료로 기록한다`() {
        val lab = Lab(LearningPath.VIRTUAL)
        lab.toWaiting()
        assertTrue(lab.effects.isEmpty())

        lab.send(LessonEvent.ModelActed(LEFT_ACTIVE))
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
        assertEquals(ObservedResult(LEFT_ACTIVE, true, LearningPath.VIRTUAL), lab.state.observed)
        assertEquals(LEFT_ACTIVE, lab.state.modelCode)

        lab.fromObservedToComplete()
        assertEquals(
            listOf(LessonEffect.RecordCompletion("turn_signal", LearningPath.VIRTUAL)),
            lab.completions(),
        )
    }

    @Test
    fun `차량 관찰은 가상 경로를 진행시키지 않는다`() {
        val lab = Lab(LearningPath.VIRTUAL)
        lab.toWaiting()

        lab.update(OFF)
        lab.update(LEFT_ACTIVE)
        lab.send(LessonEvent.LinkChanged(connected = false))
        lab.send(LessonEvent.RecheckVehicle)

        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertEquals(VehicleView.NONE, lab.state.vehicle)
        assertEquals(LinkState.OK, lab.state.waiting!!.link)
        assertTrue(lab.effects.isEmpty())
    }

    @Test
    fun `모형 조작은 차량 경로를 진행시키지 않는다`() {
        val lab = Lab(LearningPath.VEHICLE)
        lab.snapshot(OFF)
        lab.toWaiting()

        lab.send(LessonEvent.ModelActed(LEFT_ACTIVE))

        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertEquals(OFF, lab.state.modelCode)
        assertTrue(lab.completions().isEmpty())
    }

    @Test
    fun `가상 경로에서 다른 쪽을 누르면 안내를 주고 목표를 누르면 진행한다`() {
        val lab = Lab(LearningPath.VIRTUAL)
        lab.toWaiting()

        lab.send(LessonEvent.ModelActed(RIGHT_ACTIVE))
        assertEquals(LessonStage.WAITING, lab.state.stage)
        assertEquals(WaitingHint.OTHER_VALUE_OBSERVED, lab.state.waiting!!.hint)
        assertEquals(RIGHT_ACTIVE, lab.state.modelCode)

        lab.send(LessonEvent.ModelActed(OFF))
        assertEquals(WaitingHint.NONE, lab.state.waiting!!.hint)
        assertEquals(OFF, lab.state.modelCode)

        lab.send(LessonEvent.ModelActed("모르는 값"))
        assertEquals(OFF, lab.state.modelCode)

        lab.send(LessonEvent.ModelActed(LEFT_ACTIVE))
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
    }

    @Test
    fun `설명 단계에서는 어느 경로든 모형을 움직여도 진행이 바뀌지 않는다`() {
        for (path in LearningPath.entries) {
            val lab = Lab(path)
            if (path == LearningPath.VEHICLE) lab.snapshot(OFF)
            lab.toWaiting()
            if (path == LearningPath.VEHICLE) lab.update(LEFT_ACTIVE) else lab.send(LessonEvent.ModelActed(LEFT_ACTIVE))
            lab.send(LessonEvent.AcknowledgeObservation)

            lab.send(LessonEvent.ModelActed(RIGHT_ACTIVE))

            assertEquals(LessonStage.EXPLAIN, lab.state.stage)
            assertEquals(RIGHT_ACTIVE, lab.state.modelCode)
            assertEquals(LEFT_ACTIVE, lab.state.observed!!.code)
        }
    }

    @Test
    fun `가상 경로도 제한이 걸리면 멈추고 이어 하기 뒤에 재개한다`() {
        val lab = Lab(LearningPath.VIRTUAL)
        lab.toWaiting()

        lab.send(LessonEvent.PermissionChanged(allowed = false))
        lab.send(LessonEvent.ModelActed(LEFT_ACTIVE))
        assertEquals(LessonStage.WAITING, lab.state.stage)

        lab.send(LessonEvent.PermissionChanged(allowed = true))
        lab.send(LessonEvent.ModelActed(LEFT_ACTIVE))
        assertEquals(LessonStage.WAITING, lab.state.stage)

        lab.send(LessonEvent.Resume)
        assertTrue(lab.effects.isEmpty())
        lab.send(LessonEvent.ModelActed(LEFT_ACTIVE))
        assertEquals(LessonStage.OBSERVED, lab.state.stage)
    }

    // ---- 저장한 지점에서 이어 하기 ----

    @Test
    fun `저장한 설명 단계에서 이어 해도 완료 기록은 한 번이다`() {
        val engine = LessonEngine.restore(spec, LearningPath.VEHICLE, LessonStage.EXPLAIN, predictionMatched = false)
        assertEquals(LessonStage.EXPLAIN, engine.state.stage)
        assertEquals(ObservedResult(LEFT_ACTIVE, false, LearningPath.VEHICLE), engine.state.observed)

        val effects = mutableListOf<LessonEffect>()
        effects += engine.dispatch(LessonEvent.FinishExplanation)
        effects += engine.dispatch(LessonEvent.Answer("direction"))
        effects += engine.dispatch(LessonEvent.Answer("direction"))

        assertEquals(LessonStage.COMPLETE, engine.state.stage)
        assertEquals(
            listOf<LessonEffect>(LessonEffect.RecordCompletion("turn_signal", LearningPath.VEHICLE)),
            effects,
        )
    }

    @Test
    fun `관찰 전 단계는 저장한 지점으로 복원하지 않는다`() {
        for (stage in listOf(LessonStage.READY, LessonStage.PREDICT, LessonStage.WAITING, LessonStage.OBSERVED, LessonStage.COMPLETE)) {
            try {
                LessonEngine.restore(spec, LearningPath.VEHICLE, stage, predictionMatched = null)
                fail("$stage 단계는 복원되면 안 된다")
            } catch (expected: IllegalArgumentException) {
                // 기대한 동작
            }
        }
    }

    // ---- 콘텐츠 계약 ----

    @Test
    fun `엔진이 받는 실험 정보는 앞뒤가 맞아야 한다`() {
        val invalid = listOf<() -> LessonSpec>(
            { spec.copy(lessonId = " ") },
            { spec.copy(targetCode = OFF) },
            { spec.copy(predictionOptions = emptyMap()) },
            { spec.copy(correctCheckOptionId = "없는 선택지") },
        )
        for (build in invalid) {
            try {
                build()
                fail("잘못된 실험 정보가 통과했다")
            } catch (expected: IllegalArgumentException) {
                // 기대한 동작
            }
        }
    }
}
