package com.carsciencelab.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SDK의 ErrorCode를 흉내 낸 enum. 실제 타입은 compile classpath에 없을 수 있어 이름으로만 다룬다.
 * ERROR_SOMETHING_NEW를 뺀 이름은 문서에 나온 것이다.
 */
private enum class ErrorCode {
    ERROR_INTERNAL_SERVICE,
    ERROR_SERVICE_CONNECTION_FAILURE,
    ERROR_DRIVING_RESTRICTED,
    ERROR_NOT_SUPPORTED_PROPERTY,
    ERROR_NOT_SUPPORTED_VALUE_TYPE,
    ERROR_NOT_SUPPORTED_AREA,
    ERROR_EMPTY_RESULT,
    ERROR_SECURITY_PERMISSION,
    ERROR_UNDEFINED,
    ERROR_SOMETHING_NEW,
}

/** 플랫폼의 IPC 단절 예외를 흉내 낸다. 이름으로만 알아본다. */
private open class FakeRemoteException : Exception("remote")

private class FakeDeadObjectException : FakeRemoteException()

/** 난독화된 SDK 예외를 흉내 낸다. 메서드 이름에 의미가 없다. */
private class ObfuscatedSdkException(private val code: ErrorCode) : Exception("sdk failure") {
    @Suppress("unused")
    fun a(): ErrorCode = code
}

/** 문서에 나온 형태(getErrorCode)를 흉내 낸다. */
private class DocumentedSdkException(val errorCode: ErrorCode) : Exception("sdk failure")

/** SDK의 방향지시등 enum을 흉내 낸다. */
private enum class SdkTurnSignal { OFF, LEFT, RIGHT, EMERGENCY }

class VehicleMappingTest {

    // ---- 오류 분류 ----

    @Test
    fun `난독화된 예외에서도 오류 코드를 읽어 미지원으로 분류한다`() {
        val property = VehicleErrorClassifier.classify(ObfuscatedSdkException(ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY))
        val area = VehicleErrorClassifier.classify(ObfuscatedSdkException(ErrorCode.ERROR_NOT_SUPPORTED_AREA))

        assertEquals(VehicleFailureKind.UNSUPPORTED, property.kind)
        assertEquals("ERROR_NOT_SUPPORTED_PROPERTY", property.code)
        assertEquals(VehicleFailureKind.UNSUPPORTED, area.kind)
        assertEquals(SupportResult.Unsupported, VehicleErrorClassifier.toSupportResult(property))
    }

    @Test
    fun `문서에 나온 형태의 예외에서도 오류 코드를 읽는다`() {
        val failure = VehicleErrorClassifier.classify(DocumentedSdkException(ErrorCode.ERROR_SECURITY_PERMISSION))

        assertEquals(VehicleFailureKind.PERMISSION, failure.kind)
        assertEquals("ERROR_SECURITY_PERMISSION", failure.code)
        assertEquals(SupportResult.PermissionDenied, VehicleErrorClassifier.toSupportResult(failure))
    }

    @Test
    fun `플랫폼의 권한 예외는 권한 오류로 분류한다`() {
        val failure = VehicleErrorClassifier.classify(SecurityException("requires permission"))

        assertEquals(VehicleFailureKind.PERMISSION, failure.kind)
        assertNull(failure.code)
    }

    @Test
    fun `서비스 연결 오류는 연결 문제로 분류한다`() {
        val failure =
            VehicleErrorClassifier.classify(ObfuscatedSdkException(ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE))

        assertEquals(VehicleFailureKind.CONNECTION, failure.kind)
        assertEquals(
            SupportResult.CheckFailed(CheckFailure.CONNECTION, "ERROR_SERVICE_CONNECTION_FAILURE"),
            VehicleErrorClassifier.toSupportResult(failure),
        )
    }

    @Test
    fun `IPC가 끊긴 예외는 오류 코드가 없어도 연결 문제로 분류한다`() {
        assertEquals(VehicleFailureKind.CONNECTION, VehicleErrorClassifier.classify(FakeRemoteException()).kind)
        assertEquals(VehicleFailureKind.CONNECTION, VehicleErrorClassifier.classify(FakeDeadObjectException()).kind)
    }

    @Test
    fun `문서의 미지원 오류 코드는 모두 미지원으로 분류한다`() {
        for (code in listOf(
            ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY,
            ErrorCode.ERROR_NOT_SUPPORTED_VALUE_TYPE,
            ErrorCode.ERROR_NOT_SUPPORTED_AREA,
        )) {
            assertEquals(code.name, VehicleFailureKind.UNSUPPORTED, VehicleErrorClassifier.classify(ObfuscatedSdkException(code)).kind)
        }
    }

    @Test
    fun `값이 없다는 오류는 따로 구분하고 지원 확인에서는 확인 실패로 둔다`() {
        val failure = VehicleErrorClassifier.classify(ObfuscatedSdkException(ErrorCode.ERROR_EMPTY_RESULT))

        assertEquals(VehicleFailureKind.EMPTY, failure.kind)
        assertEquals(
            SupportResult.CheckFailed(CheckFailure.UNKNOWN, "ERROR_EMPTY_RESULT"),
            VehicleErrorClassifier.toSupportResult(failure),
        )
    }

    @Test
    fun `내부 서비스 오류와 주행 제한 오류는 연결 문제나 미지원으로 단정하지 않는다`() {
        for (code in listOf(
            ErrorCode.ERROR_INTERNAL_SERVICE,
            ErrorCode.ERROR_DRIVING_RESTRICTED,
            ErrorCode.ERROR_UNDEFINED,
        )) {
            assertEquals(code.name, VehicleFailureKind.UNKNOWN, VehicleErrorClassifier.classify(ObfuscatedSdkException(code)).kind)
        }
    }

    @Test
    fun `오류 코드가 없는 예외는 메시지로 미지원과 권한을 가린다`() {
        // 에뮬레이터에서 미지원 영역을 조회했을 때 받았다고 기록된 형태.
        val unsupported = VehicleErrorClassifier.classify(
            Exception("IllegalArgumentException: area ID: 0x100 not supported for property ID: DOOR_POS"),
        )
        val permission = VehicleErrorClassifier.classify(
            Exception("java.lang.SecurityException: requires pleos.car.permission.CAR_LIGHTS"),
        )
        val other = VehicleErrorClassifier.classify(Exception("timeout while waiting"))
        val noMessage = VehicleErrorClassifier.classify(Exception())

        assertEquals(VehicleFailureKind.UNSUPPORTED, unsupported.kind)
        assertNull(unsupported.code)
        assertEquals(VehicleFailureKind.PERMISSION, permission.kind)
        assertEquals(VehicleFailureKind.UNKNOWN, other.kind)
        assertEquals(VehicleFailureKind.UNKNOWN, noMessage.kind)
    }

    @Test
    fun `모르는 오류 코드와 일반 예외를 미지원으로 분류하지 않는다`() {
        val newCode = VehicleErrorClassifier.classify(ObfuscatedSdkException(ErrorCode.ERROR_SOMETHING_NEW))
        val plain = VehicleErrorClassifier.classify(IllegalStateException("boom"))

        assertEquals(VehicleFailureKind.UNKNOWN, newCode.kind)
        assertEquals("ERROR_SOMETHING_NEW", newCode.code)
        assertEquals(VehicleFailureKind.UNKNOWN, plain.kind)
        assertNull(plain.code)
        assertEquals(
            SupportResult.CheckFailed(CheckFailure.UNKNOWN, "ERROR_SOMETHING_NEW"),
            VehicleErrorClassifier.toSupportResult(newCode),
        )
        assertEquals(
            SupportResult.CheckFailed(CheckFailure.UNKNOWN, "java.lang.IllegalStateException"),
            VehicleErrorClassifier.toSupportResult(plain),
        )
    }

    // ---- 방향지시등 값 ----

    @Test
    fun `SDK 값을 이름으로 판별해 앱 내부 값으로 바꾼다`() {
        assertEquals(ObservedValue.Known(TurnSignalCodes.OFF), TurnSignalMapper.fromSdk(SdkTurnSignal.OFF))
        assertEquals(ObservedValue.Known(TurnSignalCodes.LEFT_ACTIVE), TurnSignalMapper.fromSdk(SdkTurnSignal.LEFT))
        assertEquals(ObservedValue.Known(TurnSignalCodes.RIGHT_ACTIVE), TurnSignalMapper.fromSdk(SdkTurnSignal.RIGHT))
    }

    @Test
    fun `모르는 값과 없는 값을 꺼짐으로 바꾸지 않는다`() {
        assertEquals(ObservedValue.Unknown("EMERGENCY"), TurnSignalMapper.fromSdk(SdkTurnSignal.EMERGENCY))
        assertEquals(ObservedValue.Unknown("2"), TurnSignalMapper.fromSdk(2))
        assertNull(TurnSignalMapper.fromSdk(null))
    }

    @Test
    fun `신호 키는 콘텐츠에 적는 id로 찾는다`() {
        assertEquals(SignalKey.TURN_SIGNAL_STATE, SignalKey.fromId("turn_signal_state"))
        assertNull(SignalKey.fromId("wiper_state"))
    }
}
