package com.carsciencelab.vehicle

/**
 * SDK가 준 방향지시등 값을 앱 내부 값으로 바꾼다.
 *
 * SDK enum의 이름(OFF, LEFT, RIGHT)으로만 판별한다. 이름이 다르면 [ObservedValue.Unknown]으로
 * 남기고, 값이 없으면 null을 돌려준다. 어느 쪽도 꺼짐으로 바꾸지 않는다.
 */
object TurnSignalMapper {

    fun fromSdk(raw: Any?): ObservedValue? {
        if (raw == null) return null
        val name = (raw as? Enum<*>)?.name ?: raw.toString()
        return when (name) {
            "OFF" -> ObservedValue.Known(TurnSignalCodes.OFF)
            "LEFT" -> ObservedValue.Known(TurnSignalCodes.LEFT_ACTIVE)
            "RIGHT" -> ObservedValue.Known(TurnSignalCodes.RIGHT_ACTIVE)
            else -> ObservedValue.Unknown(name)
        }
    }
}
