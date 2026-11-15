package com.carsciencelab.ui

import com.carsciencelab.content.LessonCatalog
import com.carsciencelab.content.LessonContentException
import com.carsciencelab.data.ResumePoint
import com.carsciencelab.learning.LearningMode
import com.carsciencelab.learning.LearningPath
import com.carsciencelab.learning.LessonStage
import com.carsciencelab.learning.LessonState
import com.carsciencelab.learning.LinkState
import com.carsciencelab.learning.SafetyHold
import com.carsciencelab.learning.VehicleValidity
import com.carsciencelab.learning.VehicleView
import com.carsciencelab.learning.WaitingHint
import com.carsciencelab.policy.BlockReason
import com.carsciencelab.policy.LearningPermission
import com.carsciencelab.policy.ParkingState
import com.carsciencelab.policy.SafetySnapshot
import com.carsciencelab.policy.UxRestrictionState
import com.carsciencelab.testsupport.FakeLessonRepository
import com.carsciencelab.testsupport.FakeSafetyMonitor
import com.carsciencelab.testsupport.FakeVehicleGateway
import com.carsciencelab.testsupport.InMemoryRecordStore
import com.carsciencelab.testsupport.PARKED_AND_UNRESTRICTED
import com.carsciencelab.testsupport.TestAssets
import com.carsciencelab.vehicle.CheckFailure
import com.carsciencelab.vehicle.ConnectionState
import com.carsciencelab.vehicle.ObservedValue
import com.carsciencelab.vehicle.SupportResult
import com.carsciencelab.vehicle.TurnSignalCodes.LEFT_ACTIVE
import com.carsciencelab.vehicle.TurnSignalCodes.OFF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabControllerTest {

    private val scopes = mutableListOf<CoroutineScope>()
    private val lessonId = "turn_signal"

    private inner class Env(
        safetySnapshot: SafetySnapshot = PARKED_AND_UNRESTRICTED,
        support: SupportResult = SupportResult.Supported,
        loadCatalog: () -> LessonCatalog = { TestAssets.catalog() },
        prepare: (FakeVehicleGateway) -> Unit = {},
    ) {
        val gateway = FakeVehicleGateway().also {
            it.support = support
            prepare(it)
        }
        val safety = FakeSafetyMonitor(safetySnapshot)
        val store = InMemoryRecordStore()
        val storageErrors = mutableListOf<Throwable>()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()).also { scopes += it }
        val controller = LabController(
            scope = scope,
            gateway = gateway,
            safety = safety,
            lessons = FakeLessonRepository(loadCatalog),
            store = store,
            nowEpochMs = { 1_000L },
            onStorageError = { storageErrors += it },
        )

        init {
            controller.start()
            controller.onForegroundChanged(true)
        }

        val ui: LabUiState get() = controller.state.value
        val lesson: LessonState get() = ui.lesson!!.state

        fun startVehicleLessonAndWait(option: String = "left") {
            controller.startLesson(lessonId, LearningPath.VEHICLE)
            controller.begin()
            controller.predict(option)
        }

        fun finishFromObserved() {
            controller.acknowledgeObservation()
            controller.finishExplanation()
            controller.answer("direction")
        }
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    // ---- 시작 ----

    @Test
    fun `시작하면 콘텐츠를 읽고 차량에서 관찰할 수 있는지 확인한다`() {
        val env = Env()

        assertFalse(env.ui.loading)
        assertEquals(LearningPermission.Allowed, env.ui.gate)
        assertTrue(env.safety.started)
        assertEquals(1, env.ui.cards.size)
        assertEquals(VehicleAvailability.AVAILABLE, env.ui.cards.single().availability)
        assertTrue(env.ui.cards.single().canObserveInVehicle)
        assertFalse(env.ui.cards.single().offersVirtual)
        assertEquals(listOf("wiper", "recirculation"), env.ui.planned.map { it.id })
        assertEquals(1, env.gateway.capabilityCalls)
        assertNull(env.ui.contentError)
    }

    @Test
    fun `콘텐츠를 읽지 못하면 오류를 알리고 실험을 열지 않는다`() {
        val env = Env(loadCatalog = { throw LessonContentException("형식 오류") })

        assertFalse(env.ui.loading)
        assertEquals("형식 오류", env.ui.contentError)
        assertTrue(env.ui.cards.isEmpty())

        env.controller.startLesson(lessonId, LearningPath.VIRTUAL)
        assertNull(env.ui.lesson)
    }

    // ---- 학습 허용 조건 ----

    @Test
    fun `주차를 확인하지 못하면 차량에 묻지 않고 가상 실험도 시작하지 않는다`() {
        val env = Env(safetySnapshot = SafetySnapshot.UNKNOWN)

        assertEquals(LearningPermission.Blocked(BlockReason.PARKING_UNKNOWN), env.ui.gate)
        assertEquals(0, env.gateway.capabilityCalls)
        assertEquals(VehicleAvailability.NOT_CHECKED, env.ui.cards.single().availability)

        env.controller.startLesson(lessonId, LearningPath.VIRTUAL)
        env.controller.startLesson(lessonId, LearningPath.VEHICLE)
        assertNull(env.ui.lesson)
        assertTrue(env.gateway.subscriptions.isEmpty())
    }

    @Test
    fun `제한이 걸리면 구독을 해제하고 풀린 뒤 이어 하기를 눌러야 다시 구독한다`() {
        val env = Env()
        env.startVehicleLessonAndWait()
        assertEquals(listOf("session-1"), env.gateway.subscriptions)

        env.safety.snapshot.value = SafetySnapshot(ParkingState.NOT_PARKED, UxRestrictionState.RESTRICTED)
        assertEquals(LearningPermission.Blocked(BlockReason.UX_RESTRICTED), env.ui.gate)
        assertTrue(env.gateway.subscriptions.isEmpty())
        assertEquals(SafetyHold.BLOCKED, env.lesson.hold)
        assertEquals(VehicleAvailability.NOT_CHECKED, env.ui.cards.single().availability)

        // 멈춘 동안 켠 신호는 관찰로 인정되지 않는다.
        env.gateway.emit(LEFT_ACTIVE)
        assertEquals(LessonStage.WAITING, env.lesson.stage)

        env.safety.snapshot.value = PARKED_AND_UNRESTRICTED
        assertEquals(LearningPermission.Allowed, env.ui.gate)
        assertEquals(SafetyHold.AWAITING_RESUME, env.lesson.hold)
        assertTrue(env.gateway.subscriptions.isEmpty())
        assertEquals(2, env.gateway.capabilityCalls)

        env.controller.resumeAfterHold()
        assertEquals(SafetyHold.NONE, env.lesson.hold)
        assertEquals(listOf("session-2"), env.gateway.subscriptions)
        assertEquals(LessonStage.WAITING, env.lesson.stage)
        assertEquals(WaitingHint.ALREADY_AT_TARGET, env.lesson.waiting!!.hint)

        env.gateway.emit(OFF)
        env.gateway.emit(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, env.lesson.stage)
    }

    @Test
    fun `화면이 보이지 않으면 멈추고 돌아와도 자동으로 재개하지 않는다`() {
        val env = Env()
        env.startVehicleLessonAndWait()

        env.controller.onForegroundChanged(false)
        assertEquals(LearningPermission.Blocked(BlockReason.NOT_FOREGROUND), env.ui.gate)
        assertTrue(env.gateway.subscriptions.isEmpty())

        env.gateway.emit(LEFT_ACTIVE)
        env.controller.onForegroundChanged(true)

        assertEquals(SafetyHold.AWAITING_RESUME, env.lesson.hold)
        assertTrue(env.gateway.subscriptions.isEmpty())
        assertEquals(LessonStage.WAITING, env.lesson.stage)
    }

    // ---- 차량 지원 여부 ----

    @Test
    fun `미지원이면 차량 관찰은 막고 가상 실험을 권한다`() {
        val env = Env(support = SupportResult.Unsupported)
        val card = env.ui.cards.single()

        assertEquals(VehicleAvailability.UNSUPPORTED, card.availability)
        assertFalse(card.canObserveInVehicle)
        assertTrue(card.offersVirtual)

        env.controller.startLesson(lessonId, LearningPath.VEHICLE)
        assertNull(env.ui.lesson)

        env.controller.startLesson(lessonId, LearningPath.VIRTUAL)
        assertEquals(LearningPath.VIRTUAL, env.lesson.path)
        assertEquals(LabScreen.LESSON, env.ui.screen)
        assertTrue(env.gateway.subscriptions.isEmpty())
        assertEquals(0, env.gateway.readCalls)
    }

    @Test
    fun `지원 확인이 실패한 사유를 미지원과 구분한다`() {
        val expected = mapOf(
            SupportResult.PermissionDenied to VehicleAvailability.PERMISSION_DENIED,
            SupportResult.CheckFailed(CheckFailure.CONNECTION) to VehicleAvailability.CONNECTION_FAILED,
            SupportResult.CheckFailed(CheckFailure.TIMEOUT) to VehicleAvailability.CONNECTION_FAILED,
            SupportResult.CheckFailed(CheckFailure.UNKNOWN) to VehicleAvailability.CHECK_FAILED,
        )
        for ((support, availability) in expected) {
            val env = Env(support = support)
            assertEquals(availability, env.ui.cards.single().availability)
            assertTrue(env.ui.cards.single().offersVirtual)
        }
    }

    @Test
    fun `다시 확인하면 차량에 한 번 더 묻는다`() {
        val env = Env(support = SupportResult.CheckFailed(CheckFailure.CONNECTION))
        env.gateway.support = SupportResult.Supported

        env.controller.refreshAvailability()

        assertEquals(2, env.gateway.capabilityCalls)
        assertEquals(VehicleAvailability.AVAILABLE, env.ui.cards.single().availability)
    }

    // ---- 실험 진행 ----

    @Test
    fun `차량 실험을 끝까지 진행하면 차량 관찰 완료로 한 번만 기록한다`() {
        val env = Env()
        env.controller.startLesson(lessonId, LearningPath.VEHICLE)

        assertEquals(LabScreen.LESSON, env.ui.screen)
        assertEquals(listOf("session-1"), env.gateway.subscriptions)
        assertEquals(1, env.gateway.readCalls)
        assertEquals(VehicleView(VehicleValidity.VALID, OFF), env.lesson.vehicle)

        env.controller.begin()
        env.controller.predict("left")
        assertEquals(LessonStage.WAITING, env.lesson.stage)

        env.gateway.emit(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, env.lesson.stage)

        env.finishFromObserved()
        env.controller.answer("direction")
        assertEquals(LessonStage.COMPLETE, env.lesson.stage)

        val record = env.store.value.records.getValue(lessonId)
        assertEquals(1, record.vehicleCompletions)
        assertEquals(0, record.virtualCompletions)
        assertEquals("VEHICLE", record.lastPath)
        assertEquals(1_000L, record.lastCompletedAtEpochMs)
        assertNull(env.store.value.resume)
        assertEquals(1, env.ui.cards.single().vehicleCompletions)
    }

    @Test
    fun `가상 실험은 가상 완료로만 센다`() {
        val env = Env(support = SupportResult.Unsupported)
        env.controller.startLesson(lessonId, LearningPath.VIRTUAL)
        env.controller.begin()
        env.controller.predict("right")

        env.controller.modelAction(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, env.lesson.stage)
        assertEquals(false, env.lesson.observed!!.predictionMatched)

        env.finishFromObserved()

        val record = env.store.value.records.getValue(lessonId)
        assertEquals(0, record.vehicleCompletions)
        assertEquals(1, record.virtualCompletions)
        assertEquals("VIRTUAL", record.lastPath)
    }

    @Test
    fun `알 수 없는 차량 값을 꺼짐으로 보여 주지 않는다`() {
        val env = Env()
        env.startVehicleLessonAndWait()

        env.gateway.emit(ObservedValue.Unknown("BOTH"))

        assertEquals(VehicleView(VehicleValidity.UNKNOWN, null), env.lesson.vehicle)
        assertEquals(WaitingHint.STATE_UNKNOWN, env.lesson.waiting!!.hint)
        assertEquals(LessonStage.WAITING, env.lesson.stage)
    }

    @Test
    fun `모드를 바꿔도 실험 진행은 유지된다`() {
        val env = Env()
        env.startVehicleLessonAndWait()
        val before = env.lesson

        env.controller.selectMode(LearningMode.UNDERSTAND)

        assertEquals(LearningMode.UNDERSTAND, env.ui.mode)
        assertEquals(before, env.lesson)
        assertEquals(LearningMode.UNDERSTAND, env.store.value.learningMode())
        assertEquals(listOf("session-1"), env.gateway.subscriptions)
    }

    @Test
    fun `차량 관찰 중 가상 실험으로 바꾸면 구독을 해제하고 가상 경로로 다시 시작한다`() {
        val env = Env()
        env.startVehicleLessonAndWait()

        env.controller.switchToVirtual()

        assertEquals(LearningPath.VIRTUAL, env.lesson.path)
        assertEquals(LessonStage.READY, env.lesson.stage)
        assertTrue(env.gateway.subscriptions.isEmpty())

        env.gateway.emit(LEFT_ACTIVE)
        env.controller.begin()
        env.controller.predict("left")
        assertEquals(LessonStage.WAITING, env.lesson.stage)
    }

    // ---- 구독 관리 ----

    @Test
    fun `실험 화면을 벗어나면 구독을 해제하고 다시 열면 하나만 구독한다`() {
        val env = Env()
        env.startVehicleLessonAndWait()

        env.controller.exitLesson()
        assertTrue(env.gateway.subscriptions.isEmpty())
        assertNull(env.ui.lesson)
        assertEquals(LabScreen.HOME, env.ui.screen)

        // 화면을 떠난 뒤의 변화로는 아무 일도 일어나지 않는다.
        env.gateway.emit(LEFT_ACTIVE)
        assertNull(env.ui.lesson)
        assertTrue(env.store.value.records.isEmpty())

        env.controller.startLesson(lessonId, LearningPath.VEHICLE)
        assertEquals(listOf("session-2"), env.gateway.subscriptions)
    }

    @Test
    fun `늦게 도착한 조회 응답은 더 새로운 알림을 덮어쓰지 않는다`() {
        val env = Env(prepare = { it.holdReads = true })
        env.controller.startLesson(lessonId, LearningPath.VEHICLE)
        assertEquals(VehicleView.NONE, env.lesson.vehicle)

        env.gateway.emit(LEFT_ACTIVE)
        env.gateway.releaseRead(ObservedValue.Known(OFF))

        assertEquals(VehicleView(VehicleValidity.VALID, LEFT_ACTIVE), env.lesson.vehicle)
        assertEquals(1, env.ui.diagnostics.droppedObservations)
    }

    @Test
    fun `실험을 나간 뒤 도착한 조회 응답은 무시한다`() {
        val env = Env(prepare = { it.holdReads = true })
        env.controller.startLesson(lessonId, LearningPath.VEHICLE)

        env.controller.exitLesson()
        env.gateway.releaseRead(ObservedValue.Known(LEFT_ACTIVE))

        assertNull(env.ui.lesson)
        assertEquals(0, env.ui.diagnostics.droppedObservations)
    }

    @Test
    fun `연결이 끊기면 대기를 멈추고 다시 확인을 누르면 새 세션으로 구독한다`() {
        val env = Env()
        env.startVehicleLessonAndWait()

        env.gateway.connection.value = ConnectionState.FAILED
        assertEquals(LinkState.LOST, env.lesson.waiting!!.link)
        assertEquals(VehicleView.NONE, env.lesson.vehicle)
        assertEquals(ConnectionState.FAILED, env.ui.diagnostics.connection)

        env.gateway.connection.value = ConnectionState.CONNECTED
        assertEquals(LinkState.AWAITING_RECHECK, env.lesson.waiting!!.link)
        env.gateway.emit(LEFT_ACTIVE)
        assertEquals(LessonStage.WAITING, env.lesson.stage)

        env.controller.refreshVehicle()
        assertEquals(listOf("session-2"), env.gateway.subscriptions)
        assertEquals(LinkState.OK, env.lesson.waiting!!.link)
        assertEquals(WaitingHint.ALREADY_AT_TARGET, env.lesson.waiting!!.hint)

        env.gateway.emit(OFF)
        env.gateway.emit(LEFT_ACTIVE)
        assertEquals(LessonStage.OBSERVED, env.lesson.stage)
    }

    @Test
    fun `종료하면 구독과 안전 감시를 정리한다`() {
        val env = Env()
        env.startVehicleLessonAndWait()

        env.controller.dispose()

        assertTrue(env.gateway.subscriptions.isEmpty())
        assertTrue(env.safety.stopped)
    }

    // ---- 기록과 이어 하기 ----

    @Test
    fun `설명 단계에서 나가면 이어 하기 지점이 남고 그 지점에서 다시 시작한다`() {
        val env = Env()
        env.startVehicleLessonAndWait()
        env.gateway.emit(LEFT_ACTIVE)
        assertNull(env.store.value.resume)

        env.controller.acknowledgeObservation()
        assertEquals(ResumePoint(lessonId, "VEHICLE", "EXPLAIN", true), env.store.value.resume)

        env.controller.exitLesson()
        val resume = env.ui.resume
        assertNotNull(resume)
        assertEquals(LessonStage.EXPLAIN, resume!!.stage)
        assertEquals(LearningPath.VEHICLE, resume.path)

        env.controller.resumeSavedLesson()
        assertEquals(LessonStage.EXPLAIN, env.lesson.stage)
        assertTrue(env.ui.lesson!!.restored)
        // 관찰은 이미 마쳤으므로 차량을 다시 구독하지 않는다.
        assertTrue(env.gateway.subscriptions.isEmpty())

        env.controller.finishExplanation()
        assertEquals(ResumePoint(lessonId, "VEHICLE", "CHECK", true), env.store.value.resume)
        env.controller.answer("direction")

        assertEquals(LessonStage.COMPLETE, env.lesson.stage)
        assertEquals(1, env.store.value.records.getValue(lessonId).vehicleCompletions)
        assertNull(env.store.value.resume)
        assertNull(env.ui.resume)
    }

    @Test
    fun `이어 한 실험을 다시 해보면 새 실험으로 열어 차량을 다시 구독한다`() {
        val env = Env()
        env.startVehicleLessonAndWait()
        env.gateway.emit(LEFT_ACTIVE)
        env.controller.acknowledgeObservation()
        env.controller.exitLesson()
        env.controller.resumeSavedLesson()
        env.controller.finishExplanation()
        env.controller.answer("direction")
        assertTrue(env.gateway.subscriptions.isEmpty())

        env.controller.restartLesson()

        assertFalse(env.ui.lesson!!.restored)
        assertEquals(LessonStage.READY, env.lesson.stage)
        assertEquals(LearningPath.VEHICLE, env.lesson.path)
        assertEquals(listOf("session-2"), env.gateway.subscriptions)
    }

    @Test
    fun `같은 실험을 처음부터 다시 열면 이어 하기 지점을 지운다`() {
        val env = Env()
        env.startVehicleLessonAndWait()
        env.gateway.emit(LEFT_ACTIVE)
        env.controller.acknowledgeObservation()
        env.controller.exitLesson()
        assertNotNull(env.store.value.resume)

        env.controller.startLesson(lessonId, LearningPath.VEHICLE)

        assertNull(env.store.value.resume)
        assertEquals(LessonStage.READY, env.lesson.stage)
    }

    @Test
    fun `기록을 지우면 완료 횟수와 이어 하기 지점이 사라지고 모드는 남는다`() {
        val env = Env()
        env.controller.selectMode(LearningMode.UNDERSTAND)
        env.startVehicleLessonAndWait()
        env.gateway.emit(LEFT_ACTIVE)
        env.finishFromObserved()
        env.controller.exitLesson()
        assertEquals(1, env.ui.cards.single().vehicleCompletions)

        env.controller.showRecords()
        assertEquals(LabScreen.RECORDS, env.ui.screen)
        env.controller.clearRecords()

        assertTrue(env.store.value.records.isEmpty())
        assertNull(env.store.value.resume)
        assertEquals(LearningMode.UNDERSTAND, env.ui.mode)
        assertEquals(0, env.ui.cards.single().vehicleCompletions)
    }

    @Test
    fun `저장에 실패해도 실험은 계속되고 실패를 알린다`() {
        val env = Env()
        env.store.failUpdates = true
        env.startVehicleLessonAndWait()
        env.gateway.emit(LEFT_ACTIVE)

        env.finishFromObserved()

        assertEquals(LessonStage.COMPLETE, env.lesson.stage)
        assertTrue(env.storageErrors.isNotEmpty())
        assertTrue(env.store.value.records.isEmpty())
    }
}
