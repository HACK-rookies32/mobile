package com.hack.kidhack.security

/**
 * 앱 시작 시([com.hack.kidhack.KidhackApplication.onCreate]) 돌린 방어 검사 결과를
 * UI 로 전달하기 위한 보관소.
 *
 * [왜 필요한가] onCreate 는 Application 컨텍스트라 화면(다이얼로그)을 띄울 수 없다.
 * 그래서 시작 검사는 여기에 결과만 담아두고, MainActivity 의 루트 게이트가 꺼내
 * 다이얼로그를 그린다. (검사 시점과 표시 시점의 분리)
 */
object DefenseState {

    /** 앱 시작 검사 결과. onCreate 에서 채워진다. null 이면 아직 검사 전. */
    @Volatile
    var startupResult: DefenseResult? = null
}