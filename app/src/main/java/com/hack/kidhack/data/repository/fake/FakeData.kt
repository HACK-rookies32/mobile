package com.hack.kidhack.data.repository.fake

import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.Subscription
import com.hack.kidhack.domain.model.User

object FakeData {
    const val PAIRING_CODE = "382194"

    val parent = User(
        id = "user-parent",
        loginId = "parent01",
        name = "김지영",
        role = Role.PARENT,
        phone = "010-1234-5678",
        email = "kim@email.com",
    )

    val childUser = User(
        id = "user-child",
        loginId = "minjun",
        name = "김민준",
        role = Role.CHILD,
        phone = "010-8765-4321",
        email = "minjun@email.com",
    )

    val minjun = ChildProfile(
        id = "child-minjun",
        name = "김민준",
        alias = "민준",
        ageLabel = "12세 · 초등학교 6학년",
        school = "강남 초등학교",
        birthDate = "2014년 3월 15일",
        deviceModel = "Samsung Galaxy A54",
        online = true,
    )

    val jia = ChildProfile(
        id = "child-jia",
        name = "이지아",
        alias = "지아",
        ageLabel = "9세 · 초등학교 3학년",
        school = "강남 초등학교",
        birthDate = "2017년 6월 2일",
        deviceModel = "Samsung Galaxy A15",
        online = false,
    )

    val children = listOf(minjun, jia)
    // 자녀 없을 때 테스트 (empty)
    //val children = emptyList<ChildProfile>()

    val usages = listOf(
        AppUsage("YouTube", 80 * 60, "동영상"),
        AppUsage("카카오톡", 35 * 60, "메신저"),
        AppUsage("Chrome", 22 * 60, "브라우저"),
    )

    val chatMessages = listOf(
        ChatMessage("m1", "room-minjun", Role.PARENT, "민준아, 지금 어디야?", "14:30"),
        ChatMessage("m2", "room-minjun", Role.CHILD, "엄마 저 학원이요! 6시에 끝나요", "14:31"),
        ChatMessage("m3", "room-minjun", Role.PARENT, "알겠어 🙂 공부 열심히!", "14:31"),
        ChatMessage("m4", "room-minjun", Role.CHILD, "네~! 사랑해요 ❤️", "14:32"),
    )

    val posts = listOf(
        Post(
            id = "p1",
            title = "유튜브 시청 시간 제한, 어떻게 하시나요?",
            author = "행복한엄마",
            commentCount = 2,
            likeCount = 24,
            createdAtLabel = "2시간 전",
            comments = listOf(
                Comment("c1", "도전맘", "저는 하루 1시간으로 정해두고 있어요.", "1시간 전"),
                Comment("c2", "민준아빠", "약속을 정하는 게 가장 중요한 것 같아요.", "30분 전")
            )
        ),
        Post("p2", "스마트폰 처음 사준 시기가 언제예요?", "두아이아빠", 15, 37, "5시간 전"),
        Post("p3", "루키즈 앱 쓰고 마음이 편해졌어요 후기", "워킹맘", 5, 18, "어제"),
    )

    val payments = listOf(
        Payment("pay1", "민준 용돈 송금", -10_000, "08.20", "out"),
        Payment("pay2", "충전", 50_000, "08.15", "in"),
        Payment("pay3", "구독 결제", -9_900, "08.01", "out"),
    )

    val pointBalance = PointBalance(45_000)

    val pairing = PairingCode(PAIRING_CODE, 300)

    const val CHILD_SCREEN_USED_MINUTES = 84
    const val CHILD_SCREEN_LIMIT_MINUTES = 180
    const val CHILD_BLOCKED_TODAY = 3
    const val CHILD_GPS_ENABLED = true
    const val CHILD_CHAT_ROOM_ID = "room-minjun"

    fun sessionFor(user: User): Session = Session(
        accessToken = "mock-access-${user.id}",
        refreshToken = "mock-refresh-${user.id}",
        user = user,
    )
}
