package com.carsciencelab.learning

import com.carsciencelab.vehicle.Observation
import com.carsciencelab.vehicle.ObservationKind
import com.carsciencelab.vehicle.ObservationQuality
import com.carsciencelab.vehicle.ObservationSource
import com.carsciencelab.vehicle.ObservedValue
import com.carsciencelab.vehicle.SignalKey
import com.carsciencelab.vehicle.TurnSignalCodes.LEFT_ACTIVE
import com.carsciencelab.vehicle.TurnSignalCodes.OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationInterpreterTest {

    private fun observation(
        kind: ObservationKind,
        session: String = "s1",
        value: ObservedValue? = ObservedValue.Known(OFF),
    ) = Observation.of(
        signal = SignalKey.TURN_SIGNAL_STATE,
        value = value,
        receivedAtElapsedMs = 0L,
        source = ObservationSource.SIMULATION,
        kind = kind,
        sessionId = session,
    )

    private fun accepted(result: Interpretation): SequencedObservation =
        (result as Interpretation.Accepted).observation

    @Test
    fun `세션을 시작하기 전의 관찰은 버린다`() {
        val interpreter = ObservationInterpreter()

        val result = interpreter.accept(observation(ObservationKind.UPDATE))

        assertEquals(Interpretation.Dropped(DropReason.NO_ACTIVE_SESSION), result)
        assertNull(interpreter.sessionId)
    }

    @Test
    fun `세션을 끝낸 뒤의 관찰은 버린다`() {
        val interpreter = ObservationInterpreter()
        interpreter.startSession("s1")
        interpreter.endSession()

        val result = interpreter.accept(observation(ObservationKind.UPDATE))

        assertEquals(Interpretation.Dropped(DropReason.NO_ACTIVE_SESSION), result)
    }

    @Test
    fun `현재 세션이 아닌 관찰은 버린다`() {
        val interpreter = ObservationInterpreter()
        interpreter.startSession("s2")

        val update = interpreter.accept(observation(ObservationKind.UPDATE, session = "s1"))
        val snapshot = interpreter.accept(observation(ObservationKind.SNAPSHOT, session = "s1"), interpreter.mark())

        assertEquals(Interpretation.Dropped(DropReason.SESSION_MISMATCH), update)
        assertEquals(Interpretation.Dropped(DropReason.SESSION_MISMATCH), snapshot)
        assertEquals(0L, interpreter.mark())
    }

    @Test
    fun `요청 표식이 없는 조회 응답은 버린다`() {
        val interpreter = ObservationInterpreter()
        interpreter.startSession("s1")

        val result = interpreter.accept(observation(ObservationKind.SNAPSHOT))

        assertEquals(Interpretation.Dropped(DropReason.MISSING_REQUEST_MARK), result)
    }

    @Test
    fun `조회를 요청한 뒤 알림을 먼저 받았다면 그 조회 응답은 버린다`() {
        val interpreter = ObservationInterpreter()
        interpreter.startSession("s1")
        val requestMark = interpreter.mark()

        interpreter.accept(observation(ObservationKind.UPDATE, value = ObservedValue.Known(LEFT_ACTIVE)))
        val stale = interpreter.accept(observation(ObservationKind.SNAPSHOT), requestMark)

        assertEquals(Interpretation.Dropped(DropReason.STALE_SNAPSHOT), stale)
    }

    @Test
    fun `알림을 받은 뒤에 요청한 조회 응답은 받아들인다`() {
        val interpreter = ObservationInterpreter()
        interpreter.startSession("s1")
        interpreter.accept(observation(ObservationKind.UPDATE))

        val requestMark = interpreter.mark()
        val fresh = interpreter.accept(observation(ObservationKind.SNAPSHOT), requestMark)

        assertEquals(requestMark, accepted(fresh).requestMark)
        assertEquals(2L, accepted(fresh).seq)
    }

    @Test
    fun `과제 시작 표식으로 그 전과 후의 알림을 구분한다`() {
        val interpreter = ObservationInterpreter()
        interpreter.startSession("s1")

        val before = accepted(interpreter.accept(observation(ObservationKind.UPDATE)))
        val taskMark = interpreter.beginTask()
        val after = accepted(interpreter.accept(observation(ObservationKind.UPDATE)))

        assertFalse(before.isAfter(taskMark))
        assertTrue(after.isAfter(taskMark))
    }

    @Test
    fun `과제 시작 전에 요청한 조회는 응답이 늦게 와도 이전 정보다`() {
        val interpreter = ObservationInterpreter()
        interpreter.startSession("s1")
        interpreter.accept(observation(ObservationKind.UPDATE))

        val requestedBefore = interpreter.mark()
        val taskMark = interpreter.beginTask()
        val late = accepted(interpreter.accept(observation(ObservationKind.SNAPSHOT), requestedBefore))

        assertFalse(late.isAfter(taskMark))
    }

    @Test
    fun `과제 시작 뒤에 요청한 조회는 이후 정보다`() {
        val interpreter = ObservationInterpreter()
        interpreter.startSession("s1")
        interpreter.accept(observation(ObservationKind.UPDATE))

        val taskMark = interpreter.beginTask()
        val requestedAfter = interpreter.mark()
        val fresh = accepted(interpreter.accept(observation(ObservationKind.SNAPSHOT), requestedAfter))

        assertTrue(fresh.isAfter(taskMark))
    }

    @Test
    fun `수신 순서는 세션이 바뀌어도 이어진다`() {
        val interpreter = ObservationInterpreter()
        interpreter.startSession("s1")
        val first = accepted(interpreter.accept(observation(ObservationKind.UPDATE, session = "s1")))

        interpreter.startSession("s2")
        val second = accepted(interpreter.accept(observation(ObservationKind.UPDATE, session = "s2")))

        assertEquals("s2", interpreter.sessionId)
        assertTrue(second.seq > first.seq)
    }

    @Test
    fun `값의 종류에 맞는 품질이 붙는다`() {
        assertEquals(
            ObservationQuality.VALID,
            observation(ObservationKind.UPDATE, value = ObservedValue.Known(OFF)).quality,
        )
        assertEquals(
            ObservationQuality.UNKNOWN_VALUE,
            observation(ObservationKind.UPDATE, value = ObservedValue.Unknown("BOTH")).quality,
        )
        assertEquals(
            ObservationQuality.UNAVAILABLE,
            observation(ObservationKind.UPDATE, value = null).quality,
        )
    }
}
