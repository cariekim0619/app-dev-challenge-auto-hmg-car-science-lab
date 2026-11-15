package com.carsciencelab.vehicle

enum class VehicleFailureKind {
    PERMISSION,
    UNSUPPORTED,

    /** 차량 서비스와의 연결 문제. 연결을 다시 열어야 한다. */
    CONNECTION,

    /** 차량 서비스가 답했지만 값이 없다. */
    EMPTY,

    /** 원인을 알 수 없다. 미지원이나 연결 문제로 단정하지 않는다. */
    UNKNOWN,
}

data class VehicleFailure(
    val kind: VehicleFailureKind,
    /** SDK ErrorCode의 이름. 찾지 못하면 null. */
    val code: String?,
    val exceptionType: String,
)

/**
 * Vehicle SDK가 onFailed로 넘긴 예외를 분류한다.
 *
 * 공식 문서는 VehicleException.errorCode로 분기하라고 안내하지만, 예외와 ErrorCode 타입이
 * compile classpath에 보이지 않는 배포본이 있다. 그래서 타입을 직접 참조하지 않고
 * 리플렉션으로 ErrorCode enum의 이름만 읽는다.
 *
 * 문서의 ErrorCode 이름을 이렇게 나눈다.
 * - ERROR_SECURITY_PERMISSION → 권한
 * - ERROR_NOT_SUPPORTED_PROPERTY, _AREA, _VALUE_TYPE → 미지원
 * - ERROR_SERVICE_CONNECTION_FAILURE → 연결 문제
 * - ERROR_EMPTY_RESULT → 값 없음
 * - ERROR_INTERNAL_SERVICE, ERROR_DRIVING_RESTRICTED, ERROR_UNDEFINED, 모르는 이름 → 원인 미상
 *
 * 오류 코드를 읽지 못하면 메시지로만 가리고, 그것도 안 되면 원인 미상으로 둔다.
 * 알 수 없는 예외를 임의로 미지원으로 분류하지 않는다.
 */
object VehicleErrorClassifier {

    private val CONNECTION_HINTS = listOf("CONNECT", "BINDER", "REMOTE")

    /** 차량 서비스와의 IPC가 끊겼을 때 플랫폼이 던지는 예외(android.os.RemoteException 계열)의 이름. */
    private val REMOTE_EXCEPTION_NAMES = listOf("RemoteException", "DeadObjectException")

    fun classify(error: Throwable): VehicleFailure {
        val code = errorCodeName(error)
        val kind = when {
            error is SecurityException -> VehicleFailureKind.PERMISSION
            isRemoteFailure(error) -> VehicleFailureKind.CONNECTION
            code != null -> kindFromCode(code)
            else -> kindFromMessage(error.message)
        }
        return VehicleFailure(kind = kind, code = code, exceptionType = error.javaClass.name)
    }

    fun toSupportResult(failure: VehicleFailure): SupportResult = when (failure.kind) {
        VehicleFailureKind.PERMISSION -> SupportResult.PermissionDenied
        VehicleFailureKind.UNSUPPORTED -> SupportResult.Unsupported
        VehicleFailureKind.CONNECTION -> SupportResult.CheckFailed(CheckFailure.CONNECTION, failure.code)
        VehicleFailureKind.EMPTY, VehicleFailureKind.UNKNOWN ->
            SupportResult.CheckFailed(CheckFailure.UNKNOWN, failure.code ?: failure.exceptionType)
    }

    private fun kindFromCode(code: String): VehicleFailureKind = when {
        code.contains("PERMISSION") -> VehicleFailureKind.PERMISSION
        code.contains("NOT_SUPPORTED") -> VehicleFailureKind.UNSUPPORTED
        code.contains("EMPTY_RESULT") -> VehicleFailureKind.EMPTY
        CONNECTION_HINTS.any { code.contains(it) } -> VehicleFailureKind.CONNECTION
        else -> VehicleFailureKind.UNKNOWN
    }

    /**
     * 오류 코드가 없는 예외를 메시지로 가린다. 에뮬레이터에서 미지원 요청이 오류 코드 없이
     * "... not supported for property ..." 메시지의 일반 예외로 온다는 공개 기록을 따랐다
     * (docs/verified-features.md). 그 밖의 메시지는 원인 미상으로 둔다.
     */
    private fun kindFromMessage(message: String?): VehicleFailureKind {
        val text = message?.lowercase() ?: return VehicleFailureKind.UNKNOWN
        return when {
            "securityexception" in text || "permission" in text -> VehicleFailureKind.PERMISSION
            "not supported" in text -> VehicleFailureKind.UNSUPPORTED
            else -> VehicleFailureKind.UNKNOWN
        }
    }

    /** 예외나 그 상위 타입의 이름으로 IPC 단절을 알아본다. 이 파일은 Android 타입을 참조하지 않는다. */
    private fun isRemoteFailure(error: Throwable): Boolean {
        var type: Class<*>? = error.javaClass
        while (type != null) {
            val name = type.simpleName
            if (REMOTE_EXCEPTION_NAMES.any { name.endsWith(it) }) return true
            type = type.superclass
        }
        return false
    }

    /** 예외의 인자 없는 공개 메서드 중 ErrorCode enum을 돌려주는 것을 찾아 이름을 읽는다. */
    internal fun errorCodeName(error: Throwable): String? {
        val methods = try {
            error.javaClass.methods
        } catch (_: Throwable) {
            return null
        }
        for (method in methods) {
            if (method.parameterTypes.isNotEmpty()) continue
            val returnType = method.returnType
            if (!returnType.isEnum) continue
            val value = try {
                method.isAccessible = true
                method.invoke(error) as? Enum<*>
            } catch (_: Throwable) {
                null
            } ?: continue
            if (returnType.simpleName == "ErrorCode" || value.name.startsWith("ERROR_")) {
                return value.name
            }
        }
        return null
    }
}
