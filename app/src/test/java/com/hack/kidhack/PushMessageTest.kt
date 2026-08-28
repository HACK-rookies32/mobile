package com.hack.kidhack

import com.hack.kidhack.background.push.PushMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FCM data 페이로드 파싱 검증.
 *
 * 잘못된 페이로드로 앱이 죽지 않고 UNKNOWN 으로 떨어지는지, 자녀 명령의 requestId 가
 * id 로 잡히는지 확인한다.
 */
class PushMessageTest {

    @Test
    fun `보호자 알림 페이로드를 파싱한다`() {
        val msg = PushMessage.fromData(
            mapOf(
                "kind" to "PARENT_ALERT",
                "type" to "HARMFUL",
                "title" to "유해 사이트 접속 차단",
                "body" to "민준 · 도박 사이트 차단",
                "childId" to "child-minjun",
                "id" to "alert-42",
            ),
        )
        assertEquals(PushMessage.Kind.PARENT_ALERT, msg.kind)
        assertEquals("HARMFUL", msg.type)
        assertEquals("child-minjun", msg.childId)
        assertEquals("alert-42", msg.id)
    }

    @Test
    fun `자녀 명령은 requestId 를 id 로 삼는다`() {
        val msg = PushMessage.fromData(
            mapOf(
                "kind" to "CHILD_COMMAND",
                "type" to "CAMERA_SHOT",
                "requestId" to "req-7",
                "childId" to "child-minjun",
                "requestedBy" to "엄마",
            ),
        )
        assertEquals(PushMessage.Kind.CHILD_COMMAND, msg.kind)
        assertEquals("req-7", msg.id)
        assertEquals("엄마", msg.requestedBy)
    }

    @Test
    fun `kind 대소문자를 가리지 않는다`() {
        assertEquals(PushMessage.Kind.PARENT_ALERT, PushMessage.fromData(mapOf("kind" to "parent_alert")).kind)
        assertEquals(PushMessage.Kind.CHILD_COMMAND, PushMessage.fromData(mapOf("kind" to " Child_Command ")).kind)
    }

    @Test
    fun `알 수 없는 kind 나 빈 페이로드는 UNKNOWN`() {
        assertEquals(PushMessage.Kind.UNKNOWN, PushMessage.fromData(emptyMap()).kind)
        assertEquals(PushMessage.Kind.UNKNOWN, PushMessage.fromData(mapOf("kind" to "GARBAGE")).kind)
    }

    @Test
    fun `누락된 필드는 빈 문자열·null 로 안전하게 채운다`() {
        val msg = PushMessage.fromData(mapOf("kind" to "PARENT_ALERT"))
        assertEquals("", msg.type)
        assertEquals("", msg.title)
        assertEquals(null, msg.childId)
        assertEquals(null, msg.requestedBy)
    }
}
