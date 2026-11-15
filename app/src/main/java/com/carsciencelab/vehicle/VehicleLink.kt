package com.carsciencelab.vehicle

/**
 * 차량 SDK와 맞닿는 얇은 접점.
 *
 * 구현체는 SDK 호출을 그대로 옮기기만 한다. 시간 제한, 다시 연결, 값 해석, 오류 분류는
 * [SdkVehicleGateway]가 맡는다. SDK 없이도 그 규칙을 시험할 수 있게 둘을 나눴다.
 *
 * 읽기 호출만 둔다. 차량을 바꾸는 명령은 이 접점에 없다.
 * 콜백은 어느 스레드에서 불려도 되고, 값과 예외는 SDK가 준 그대로 넘긴다.
 */
interface VehicleLink {

    /** SDK 객체를 만들고 차량 서비스 연결을 요청한다. 실패하면 예외를 던진다. */
    fun open()

    /** 연결을 정리한다. 이 연결로 등록한 알림도 함께 사라진다. */
    fun close()

    /** 신호를 읽을 수 있는지 묻는다. [onAccess]에는 SDK 접근 유형의 이름(READ, READ_WRITE 등)을 넘긴다. */
    fun checkAccess(signal: SignalKey, onAccess: (String) -> Unit, onError: (Throwable) -> Unit)

    /** 현재값을 한 번 묻는다. 값이 없으면 [onValue]에 null을 넘긴다. */
    fun readCurrent(signal: SignalKey, onValue: (Any?) -> Unit, onError: (Throwable) -> Unit)

    /** 상태 변화 알림을 등록하고, 같은 리스너를 해제하는 핸들을 돌려준다. */
    fun subscribe(signal: SignalKey, onValue: (Any?) -> Unit, onError: (Throwable) -> Unit): LinkSubscription
}

/** [VehicleLink.subscribe]로 등록한 알림을 해제한다. */
fun interface LinkSubscription {
    fun cancel()
}

/** 연동 과정을 기록한다. 차량 식별 정보는 넘기지 않는다. */
fun interface VehicleLog {
    fun write(warning: Boolean, message: String)
}
