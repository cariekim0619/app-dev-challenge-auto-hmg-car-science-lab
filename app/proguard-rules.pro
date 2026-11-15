# 코드 축소(minify)를 켜지 않으므로 현재는 규칙이 필요하지 않다.
# 켜게 되면 kotlinx.serialization과 Pleos Vehicle SDK의 keep 규칙을 먼저 확인한다.
# VehicleErrorClassifier는 SDK 예외를 리플렉션으로 읽으므로 SDK 클래스 이름이 바뀌면 안 된다.
