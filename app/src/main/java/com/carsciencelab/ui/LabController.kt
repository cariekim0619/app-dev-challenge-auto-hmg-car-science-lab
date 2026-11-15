package com.carsciencelab.ui

import com.carsciencelab.content.LessonCatalog
import com.carsciencelab.content.LessonContent
import com.carsciencelab.content.LessonRepository
import com.carsciencelab.data.LessonRecord
import com.carsciencelab.data.LessonRecordStore
import com.carsciencelab.data.ResumePoint
import com.carsciencelab.data.StoredLabData
import com.carsciencelab.learning.Interpretation
import com.carsciencelab.learning.LearningMode
import com.carsciencelab.learning.LearningPath
import com.carsciencelab.learning.LessonEffect
import com.carsciencelab.learning.LessonEngine
import com.carsciencelab.learning.LessonEvent
import com.carsciencelab.learning.LessonStage
import com.carsciencelab.learning.ObservationInterpreter
import com.carsciencelab.policy.BlockReason
import com.carsciencelab.policy.LearningPermission
import com.carsciencelab.policy.LearningPolicy
import com.carsciencelab.policy.SafetyMonitor
import com.carsciencelab.policy.SafetySnapshot
import com.carsciencelab.vehicle.CheckFailure
import com.carsciencelab.vehicle.ConnectionState
import com.carsciencelab.vehicle.Observation
import com.carsciencelab.vehicle.SignalKey
import com.carsciencelab.vehicle.SupportResult
import com.carsciencelab.vehicle.VehicleGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 엔진, 차량 연결, 학습 허용 조건, 기록을 한데 묶어 화면 상태를 만든다.
 *
 * Android에 의존하지 않는다. 모든 함수는 [scope]의 스레드(앱에서는 메인 스레드)에서 불러야 한다.
 *
 * 지키는 규칙
 * - 주차·UX 조건을 통과하지 못하면 차량 관찰과 가상 학습을 모두 시작하지 않는다.
 * - 선택한 실험에 필요한 신호만, 실험 화면이 떠 있는 동안에만 구독한다.
 *   제한이 걸리거나 화면을 벗어나면 구독을 해제한다. 백그라운드에서 완료가 일어나지 않는다.
 * - 차량 관찰이 안 되는 사유(미지원, 권한, 연결, 확인 실패)를 구분해 화면에 넘긴다.
 * - 차량을 바꾸는 명령은 보내지 않는다. 원리 모형 조작은 차량과 무관하다.
 */
class LabController(
    private val scope: CoroutineScope,
    private val gateway: VehicleGateway,
    private val safety: SafetyMonitor,
    private val lessons: LessonRepository,
    private val store: LessonRecordStore,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val onStorageError: (Throwable) -> Unit = {},
) {
    private val _state = MutableStateFlow(LabUiState())
    val state: StateFlow<LabUiState> = _state.asStateFlow()

    private class ActiveLesson(
        val content: LessonContent,
        val signal: SignalKey,
        val engine: LessonEngine,
        val interpreter: ObservationInterpreter,
        val restored: Boolean,
    ) {
        var sessionId: String? = null
        var observeJob: Job? = null
        var snapshotJob: Job? = null
    }

    private var started = false
    private var loaded = false
    private var contentError: String? = null
    private var catalog = LessonCatalog.EMPTY
    private var stored = StoredLabData()
    private var safetySnapshot = SafetySnapshot.UNKNOWN
    private var foreground = false
    private var gate: LearningPermission = LearningPermission.Blocked(BlockReason.NOT_FOREGROUND)
    private val availability = mutableMapOf<String, VehicleAvailability>()
    private var screen = LabScreen.HOME
    private var active: ActiveLesson? = null
    private var sessionCounter = 0
    private var droppedObservations = 0
    private var availabilityJob: Job? = null
    private val jobs = mutableListOf<Job>()

    // ---- 시작과 종료 ----

    fun start() {
        if (started) return
        started = true
        safety.start()
        jobs += scope.launch {
            safety.snapshot.collect {
                safetySnapshot = it
                onGateInputsChanged()
            }
        }
        jobs += scope.launch {
            store.data.collect {
                stored = it
                publish()
            }
        }
        jobs += scope.launch { gateway.connection.collect { onConnectionChanged(it) } }
        jobs += scope.launch {
            try {
                catalog = lessons.load()
                contentError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                catalog = LessonCatalog.EMPTY
                contentError = e.message ?: e.javaClass.simpleName
            }
            loaded = true
            publish()
            refreshAvailability()
        }
    }

    fun dispose() {
        active?.let { stopVehicleSession(it) }
        availabilityJob?.cancel()
        jobs.forEach { it.cancel() }
        jobs.clear()
        if (started) safety.stop()
        started = false
    }

    /** 화면이 보이는지 알려 준다. 보이지 않으면 학습을 멈추고 구독을 해제한다. */
    fun onForegroundChanged(isForeground: Boolean) {
        foreground = isForeground
        onGateInputsChanged()
    }

    // ---- 화면 이동과 설정 ----

    fun showHome() {
        if (active != null) {
            exitLesson()
        } else {
            screen = LabScreen.HOME
            publish()
        }
    }

    fun showRecords() {
        if (active != null) return
        screen = LabScreen.RECORDS
        publish()
    }

    /** 모드만 바꾼다. 실험 진행은 건드리지 않는다. */
    fun selectMode(mode: LearningMode) {
        if (stored.learningMode() == mode) return
        stored = stored.withMode(mode)
        publish()
        persist { it.withMode(mode) }
    }

    fun clearRecords() {
        if (active != null) return
        persist { it.cleared() }
    }

    /** 차량에서 관찰할 수 있는지 다시 확인한다. 학습이 허용된 상태에서만 차량에 묻는다. */
    fun refreshAvailability() {
        availabilityJob?.cancel()
        if (!loaded || gate !is LearningPermission.Allowed) return
        val targets = catalog.lessons
        for (lesson in targets) availability[lesson.id] = VehicleAvailability.CHECKING
        publish()
        availabilityJob = scope.launch {
            for (lesson in targets) {
                val result = gateway.capability(signalOf(lesson))
                availability[lesson.id] = toAvailability(result)
                publish()
            }
        }
    }

    // ---- 실험 시작과 종료 ----

    fun startLesson(lessonId: String, path: LearningPath) {
        if (gate !is LearningPermission.Allowed) return
        val content = catalog.find(lessonId) ?: return
        if (path == LearningPath.VEHICLE && availability[lessonId] != VehicleAvailability.AVAILABLE) return
        active?.let { stopVehicleSession(it) }

        val interpreter = ObservationInterpreter()
        val lesson = ActiveLesson(
            content = content,
            signal = signalOf(content),
            engine = LessonEngine.start(content.toSpec(), path, interpreter::beginTask),
            interpreter = interpreter,
            restored = false,
        )
        active = lesson
        screen = LabScreen.LESSON
        if (stored.resume?.lessonId == lessonId) persist { it.withResume(null) }
        if (path == LearningPath.VEHICLE) startVehicleSession(lesson)
        publish()
    }

    /** 저장해 둔 설명·이해 확인 단계에서 이어 한다. */
    fun resumeSavedLesson() {
        if (gate !is LearningPermission.Allowed || active != null) return
        val point = stored.resume ?: return
        val content = catalog.find(point.lessonId)
        val path = point.learningPath()
        val stage = point.lessonStage()
        if (content == null || path == null || stage == null) {
            persist { it.withResume(null) }
            return
        }
        active = ActiveLesson(
            content = content,
            signal = signalOf(content),
            engine = LessonEngine.restore(content.toSpec(), path, stage, point.predictionMatched),
            interpreter = ObservationInterpreter(),
            restored = true,
        )
        screen = LabScreen.LESSON
        publish()
    }

    fun exitLesson() {
        active?.let { stopVehicleSession(it) }
        active = null
        screen = LabScreen.HOME
        publish()
    }

    /** 차량 관찰을 그만두고 같은 실험을 가상 실험으로 다시 시작한다. */
    fun switchToVirtual() {
        val lesson = active ?: return
        startLesson(lesson.content.id, LearningPath.VIRTUAL)
    }

    // ---- 실험 중 입력 ----

    fun begin() = send(LessonEvent.Begin)

    fun predict(optionId: String) = send(LessonEvent.Predict(optionId))

    fun acknowledgeObservation() = send(LessonEvent.AcknowledgeObservation)

    fun finishExplanation() = send(LessonEvent.FinishExplanation)

    fun answer(optionId: String) = send(LessonEvent.Answer(optionId))

    /**
     * 처음부터 다시 한다. 저장한 지점에서 이어 한 실험은 차량을 구독하고 있지 않으므로
     * 같은 경로의 새 실험으로 연다. 차량 관찰이 지금 불가능하면 홈으로 돌아간다.
     */
    fun restartLesson() {
        val lesson = active ?: return
        if (lesson.restored) {
            val lessonId = lesson.content.id
            val path = lesson.engine.state.path
            exitLesson()
            startLesson(lessonId, path)
        } else {
            dispatch(lesson, LessonEvent.Restart)
        }
    }

    /** 현재 차량 값을 다시 확인한다. 연결 문제 뒤라면 구독부터 다시 시작한다. */
    fun refreshVehicle() = send(LessonEvent.RefreshVehicleState)

    /** 제한이 풀린 뒤 사용자가 이어 하기를 눌렀다. */
    fun resumeAfterHold() = send(LessonEvent.Resume)

    /** 원리 모형을 조작했다. 차량에는 아무 명령도 보내지 않는다. */
    fun modelAction(code: String) = send(LessonEvent.ModelActed(code))

    private fun send(event: LessonEvent) {
        val lesson = active ?: return
        dispatch(lesson, event)
    }

    private fun dispatch(lesson: ActiveLesson, event: LessonEvent) {
        val before = lesson.engine.state.stage
        val effects = lesson.engine.dispatch(event)
        val after = lesson.engine.state.stage
        if (after != before) onStageChanged(lesson, after)
        for (effect in effects) handle(lesson, effect)
        publish()
    }

    private fun onStageChanged(lesson: ActiveLesson, stage: LessonStage) {
        val state = lesson.engine.state
        when (stage) {
            LessonStage.EXPLAIN, LessonStage.CHECK -> {
                val point = ResumePoint.of(state.lessonId, state.path, stage, state.observed?.predictionMatched)
                persist { it.withResume(point) }
            }

            LessonStage.READY ->
                if (stored.resume?.lessonId == state.lessonId) persist { it.withResume(null) }

            else -> Unit
        }
    }

    private fun handle(lesson: ActiveLesson, effect: LessonEffect) {
        when (effect) {
            is LessonEffect.RecordCompletion -> {
                val at = nowEpochMs()
                persist { it.withCompletion(effect.lessonId, effect.path, at).withResume(null) }
            }

            LessonEffect.RequestSnapshot -> requestSnapshot(lesson)
            LessonEffect.RestartVehicleSession -> startVehicleSession(lesson)
        }
    }

    // ---- 학습 허용 조건 ----

    private fun onGateInputsChanged() {
        val next = LearningPolicy.evaluate(safetySnapshot, foreground)
        if (next == gate) return
        val wasAllowed = gate is LearningPermission.Allowed
        val nowAllowed = next is LearningPermission.Allowed
        gate = next
        val lesson = active
        if (wasAllowed && !nowAllowed) {
            availabilityJob?.cancel()
            for (key in availability.keys.toList()) availability[key] = VehicleAvailability.NOT_CHECKED
            if (lesson != null) {
                stopVehicleSession(lesson)
                dispatch(lesson, LessonEvent.PermissionChanged(allowed = false))
            }
        } else if (!wasAllowed && nowAllowed) {
            if (lesson != null) dispatch(lesson, LessonEvent.PermissionChanged(allowed = true))
            refreshAvailability()
        }
        publish()
    }

    // ---- 차량 구독 ----

    private fun startVehicleSession(lesson: ActiveLesson) {
        if (lesson.restored || lesson.engine.state.path != LearningPath.VEHICLE) return
        if (gate !is LearningPermission.Allowed || active !== lesson) return
        stopVehicleSession(lesson)
        sessionCounter += 1
        val sessionId = "session-$sessionCounter"
        lesson.sessionId = sessionId
        lesson.interpreter.startSession(sessionId)
        // 먼저 알림을 구독하고 그다음 현재값을 읽는다. 그 사이의 변화를 놓치지 않기 위해서다.
        lesson.observeJob = scope.launch {
            gateway.observe(lesson.signal, sessionId).collect { onObservation(lesson, it, null) }
        }
        requestSnapshot(lesson)
    }

    private fun stopVehicleSession(lesson: ActiveLesson) {
        lesson.observeJob?.cancel()
        lesson.snapshotJob?.cancel()
        lesson.observeJob = null
        lesson.snapshotJob = null
        lesson.sessionId = null
        lesson.interpreter.endSession()
    }

    private fun requestSnapshot(lesson: ActiveLesson) {
        val sessionId = lesson.sessionId ?: return
        val requestMark = lesson.interpreter.mark()
        lesson.snapshotJob?.cancel()
        lesson.snapshotJob = scope.launch {
            val observation = gateway.read(lesson.signal, sessionId)
            onObservation(lesson, observation, requestMark)
        }
    }

    private fun onObservation(lesson: ActiveLesson, observation: Observation, requestMark: Long?) {
        if (active !== lesson) return
        when (val result = lesson.interpreter.accept(observation, requestMark)) {
            is Interpretation.Accepted -> dispatch(lesson, LessonEvent.VehicleObserved(result.observation))
            is Interpretation.Dropped -> {
                droppedObservations += 1
                publish()
            }
        }
    }

    private fun onConnectionChanged(connection: ConnectionState) {
        val lesson = active
        if (lesson != null && !lesson.restored && lesson.engine.state.path == LearningPath.VEHICLE) {
            when (connection) {
                ConnectionState.FAILED -> dispatch(lesson, LessonEvent.LinkChanged(connected = false))
                ConnectionState.CONNECTED -> dispatch(lesson, LessonEvent.LinkChanged(connected = true))
                ConnectionState.IDLE, ConnectionState.CONNECTING -> Unit
            }
        }
        publish()
    }

    // ---- 보조 ----

    private fun signalOf(content: LessonContent): SignalKey =
        content.requiredSignals.firstNotNullOf { SignalKey.fromId(it) }

    private fun toAvailability(result: SupportResult): VehicleAvailability = when (result) {
        SupportResult.Supported -> VehicleAvailability.AVAILABLE
        SupportResult.Unsupported -> VehicleAvailability.UNSUPPORTED
        SupportResult.PermissionDenied -> VehicleAvailability.PERMISSION_DENIED
        is SupportResult.CheckFailed -> when (result.failure) {
            CheckFailure.CONNECTION, CheckFailure.TIMEOUT -> VehicleAvailability.CONNECTION_FAILED
            CheckFailure.UNKNOWN -> VehicleAvailability.CHECK_FAILED
        }
    }

    private fun persist(transform: (StoredLabData) -> StoredLabData) {
        scope.launch {
            try {
                store.update(transform)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onStorageError(e)
            }
        }
    }

    private fun publish() {
        val lesson = active
        _state.value = LabUiState(
            loading = !loaded,
            screen = screen,
            mode = stored.learningMode(),
            gate = gate,
            cards = catalog.lessons.map { content ->
                val record = stored.records[content.id] ?: LessonRecord()
                LessonCardUi(
                    lessonId = content.id,
                    title = content.title,
                    question = content.question,
                    availability = availability[content.id] ?: VehicleAvailability.NOT_CHECKED,
                    vehicleCompletions = record.vehicleCompletions,
                    virtualCompletions = record.virtualCompletions,
                    lastCompletedAtEpochMs = record.lastCompletedAtEpochMs,
                )
            },
            planned = catalog.planned,
            resume = resumeUi(),
            lesson = lesson?.let { ActiveLessonUi(it.content, it.engine.state, it.restored) },
            contentError = contentError,
            diagnostics = LabDiagnostics(gateway.connection.value, droppedObservations),
        )
    }

    private fun resumeUi(): ResumeUi? {
        val point = stored.resume ?: return null
        val content = catalog.find(point.lessonId) ?: return null
        val path = point.learningPath() ?: return null
        val stage = point.lessonStage() ?: return null
        return ResumeUi(content.id, content.title, path, stage)
    }
}
