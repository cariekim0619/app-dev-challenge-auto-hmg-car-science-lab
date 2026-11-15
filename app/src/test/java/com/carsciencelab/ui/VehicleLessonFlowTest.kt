package com.carsciencelab.ui

import com.carsciencelab.learning.LearningPath
import com.carsciencelab.learning.LessonStage
import com.carsciencelab.learning.LessonState
import com.carsciencelab.learning.LinkState
import com.carsciencelab.learning.VehicleValidity
import com.carsciencelab.learning.WaitingHint
import com.carsciencelab.testsupport.CodedException
import com.carsciencelab.testsupport.FakeLessonRepository
import com.carsciencelab.testsupport.FakeSafetyMonitor
import com.carsciencelab.testsupport.FakeVehicleLink
import com.carsciencelab.testsupport.InMemoryRecordStore
import com.carsciencelab.testsupport.LinkErrorCode
import com.carsciencelab.testsupport.PARKED_AND_UNRESTRICTED
import com.carsciencelab.testsupport.Reply
import com.carsciencelab.testsupport.SdkValue
import com.carsciencelab.testsupport.TestAssets
import com.carsciencelab.vehicle.ConnectionState
import com.carsciencelab.vehicle.ObservationSource
import com.carsciencelab.vehicle.SdkVehicleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * 화면 제어, 학습 엔진, 차량 게이트웨이를 실제 구현으로 묶고 SDK 접점만 가짜로 바꿔
 * 차량 실험이 처음부터 끝까지 이어지는지 본다.
 *
 * 여기서 가정한 SDK 동작(값이 없다는 답, 답이 없는 조회)은 실제 SDK에서 확인한 것이 아니다.
 * 그런 동작이 나오더라도 앱이 어떻게 반응하는지를 고정해 두는 테스트다.
 */
class VehicleLessonFlowTest {

    private val lessonId = "turn_signal"
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    private val link = FakeVehicleLink()
    private val store = InMemoryRecordStore()
    private val gateway = SdkVehicleGateway(
        link = link,
        source = ObservationSource.VEHICLE_SDK,
        elapsedMs = { 0L },
        requestTimeoutMs = 80L,
    )
    private val controller = LabController(
        scope = scope,
        gateway = gateway,
        safety = FakeSafetyMonitor(PARKED_AND_UNRESTRICTED),
        lessons = FakeLessonRepository { TestAssets.catalog() },
        store = store,
        nowEpochMs = { 1_000L },
    )

    private val ui: LabUiState get() = controller.state.value
    private val lesson: LessonState get() = ui.lesson!!.state

    private fun startApp() {
        controller.start()
        controller.onForegroundChanged(true)
        assertEquals(VehicleAvailability.AVAILABLE, ui.cards.single().availability)
    }

    private fun startLessonAndPredictLeft() {
        controller.startLesson(lessonId, LearningPath.VEHICLE)
        controller.begin()
        controller.predict("left")
    }

    /** 시간 제한이 지나야 일어나는 일을 기다린다. */
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000L * 1_000_000L
        while (!condition()) {
            if (System.nanoTime() > deadline) fail("기다렸지만 일어나지 않았다: $what")
            Thread.sleep(10)
        }
    }

    /** 시간 제한을 처리한 스레드가 남은 일을 마칠 틈을 준다. 앱에서는 모두 메인 스레드에서 차례로 일어난다. */
    private fun settle() = Thread.sleep(40)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `꺼진 상태에서 왼쪽을 켜면 관찰로 넘어가고 끝까지 하면 차량 완료로 기록한다`() {
        startApp()
        startLessonAndPredictLeft()

        assertEquals(LessonStage.WAITING, lesson.stage)
        assertEquals(WaitingHint.NONE, lesson.waiting?.hint)
        assertEquals(1, link.activeListeners)

        link.emit(SdkValue.LEFT)
        assertEquals(LessonStage.OBSERVED, lesson.stage)

        controller.acknowledgeObservation()
        controller.finishExplanation()
        controller.answer("direction")
        assertEquals(LessonStage.COMPLETE, lesson.stage)
        assertEquals(1, store.value.records[lessonId]?.vehicleCompletions)
        assertEquals(0, store.value.records[lessonId]?.virtualCompletions)

        controller.exitLesson()
        assertEquals(0, link.activeListeners)
        assertEquals(1, link.cancelCalls)
    }

    @Test
    fun `현재값이 없는 차에서는 처음 켠 것을 변화로 단정하지 않고 끄고 다시 켜면 인정한다`() {
        // 한 번도 조작하지 않은 방향지시등은 값이 없다고 답하는 경우.
        link.current = Reply.Value(null)
        assertOffThenOnIsRequired()
    }

    @Test
    fun `현재값 조회가 값 없음 오류로 끝나는 차에서도 끄고 다시 켜면 인정한다`() {
        link.current = Reply.Error(CodedException(LinkErrorCode.ERROR_EMPTY_RESULT))
        assertOffThenOnIsRequired()
    }

    private fun assertOffThenOnIsRequired() {
        startApp()
        startLessonAndPredictLeft()

        assertEquals(LessonStage.WAITING, lesson.stage)
        assertEquals(WaitingHint.STATE_UNKNOWN, lesson.waiting?.hint)
        assertEquals(LinkState.OK, lesson.waiting?.link)
        assertEquals(ConnectionState.CONNECTED, ui.diagnostics.connection)

        link.emit(SdkValue.LEFT)
        assertEquals(LessonStage.WAITING, lesson.stage)
        assertEquals(WaitingHint.ALREADY_AT_TARGET, lesson.waiting?.hint)

        link.emit(SdkValue.OFF)
        assertEquals(WaitingHint.NONE, lesson.waiting?.hint)

        link.emit(SdkValue.LEFT)
        assertEquals(LessonStage.OBSERVED, lesson.stage)
    }

    @Test
    fun `현재값 조회가 답하지 않아도 연결 실패로 멈추지 않고 알림으로 관찰을 마친다`() {
        startApp()
        link.current = Reply.Silent

        controller.startLesson(lessonId, LearningPath.VEHICLE)
        awaitUntil("첫 조회가 시간 초과로 끝난다") { lesson.vehicle.validity == VehicleValidity.UNAVAILABLE }
        settle()
        assertEquals(ConnectionState.CONNECTED, ui.diagnostics.connection)
        assertEquals(1, link.opens)

        controller.begin()
        controller.predict("left")
        assertEquals(WaitingHint.STATE_UNKNOWN, lesson.waiting?.hint)
        assertEquals(LinkState.OK, lesson.waiting?.link)

        link.emit(SdkValue.LEFT)
        link.emit(SdkValue.OFF)
        link.emit(SdkValue.LEFT)
        assertEquals(LessonStage.OBSERVED, lesson.stage)

        // 대기 중에 보낸 조회가 뒤늦게 시간 초과로 끝나도 결과를 되돌리지 않는다.
        Thread.sleep(400)
        assertEquals(LessonStage.OBSERVED, lesson.stage)
        assertEquals(VehicleValidity.VALID, lesson.vehicle.validity)
        assertEquals(ConnectionState.CONNECTED, ui.diagnostics.connection)
    }

    @Test
    fun `차량 서비스가 답하지 않으면 대기를 멈추고 다시 확인하면 새 연결로 이어 간다`() {
        startApp()
        startLessonAndPredictLeft()
        assertEquals(WaitingHint.NONE, lesson.waiting?.hint)

        // 조회도 지원 확인도 답하지 않는다.
        link.current = Reply.Silent
        link.access = Reply.Silent
        controller.refreshVehicle()
        awaitUntil("연결 문제로 대기를 멈춘다") {
            lesson.waiting?.link == LinkState.LOST && lesson.vehicle.validity != VehicleValidity.VALID
        }
        settle()
        assertEquals(ConnectionState.FAILED, ui.diagnostics.connection)

        // 끊긴 동안 온 알림으로는 진행하지 않는다.
        link.emit(SdkValue.LEFT)
        assertEquals(LessonStage.WAITING, lesson.stage)
        link.emit(SdkValue.OFF)

        // 차량 서비스가 돌아온 뒤 사용자가 다시 확인을 누른다.
        link.current = Reply.Value(SdkValue.OFF)
        link.access = Reply.Value("READ")
        controller.refreshVehicle()

        assertEquals(2, link.opens)
        assertEquals(1, link.activeListeners)
        assertEquals(LinkState.OK, lesson.waiting?.link)
        assertEquals(WaitingHint.NONE, lesson.waiting?.hint)
        assertEquals(ConnectionState.CONNECTED, ui.diagnostics.connection)

        link.emit(SdkValue.LEFT)
        assertEquals(LessonStage.OBSERVED, lesson.stage)
    }

    @Test
    fun `실험 도중 연결을 다시 열어도 알림 등록이 이어져 관찰을 마친다`() {
        startApp()
        controller.startLesson(lessonId, LearningPath.VEHICLE)
        assertEquals(1, link.subscribeCalls)

        // 지원 확인이 답하지 않아 연결을 다시 열어야 하는 상태가 된다.
        link.access = Reply.Silent
        controller.refreshAvailability()
        awaitUntil("지원 확인이 시간 초과로 끝나고 실험이 연결 문제를 안다") {
            ui.cards.single().availability == VehicleAvailability.CONNECTION_FAILED && !lesson.linkUp
        }
        settle()

        link.access = Reply.Value("READ")
        controller.refreshAvailability()
        assertEquals(VehicleAvailability.AVAILABLE, ui.cards.single().availability)
        assertEquals(2, link.opens)
        assertEquals(2, link.subscribeCalls)
        assertEquals(1, link.activeListeners)

        controller.begin()
        controller.predict("left")
        assertEquals(LinkState.OK, lesson.waiting?.link)
        assertEquals(WaitingHint.NONE, lesson.waiting?.hint)

        link.emit(SdkValue.LEFT)
        assertEquals(LessonStage.OBSERVED, lesson.stage)
    }
}
