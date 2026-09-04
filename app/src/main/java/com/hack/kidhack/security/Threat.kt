package com.hack.kidhack.security

/**
 * 방어 검사가 잡아낼 수 있는 위협 종류.
 *
 * @property label 경고 다이얼로그 등에 표시할 사용자용 한국어 설명.
 */
enum class Threat(val label: String) {
    ROOT("루팅된 기기"),
    FRIDA("Frida 등 후킹 도구 탐지"),
    DEBUGGER("디버거 연결 탐지"),
    INTEGRITY("앱 위·변조(재서명) 탐지"),
    // Lv2: 네이티브 함수가 inline hook(트램폴린) 당한 흔적(자체무결성 위반).
    TAMPER("코드 후킹(변조) 탐지"),
}

/**
 * [DefenseChecker.runAllChecks] 의 결과.
 *
 * @property threats 이번 검사에서 잡힌 위협들. 비어 있으면 안전.
 */
data class DefenseResult(val threats: Set<Threat>) {

    /** 하나라도 위협이 잡혔는가. */
    val hasThreat: Boolean get() = threats.isNotEmpty()

    /** 경고 다이얼로그 본문에 넣을, 잡힌 위협들의 줄바꿈 목록. 예: "• 루팅된 기기\n• Frida ..." */
    val description: String get() = threats.joinToString("\n") { "• ${it.label}" }
}