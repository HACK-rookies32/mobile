package com.hack.kidhack.navigation

sealed class Route(val path: String) {
    data object Splash : Route("splash")
    data object Login : Route("login")
    data object Register : Route("register")
    data object Forgot : Route("forgot")

    data object ParentGraph : Route("parent")
    data object ParentDashboard : Route("parent/dashboard")
    data object ParentLocation : Route("parent/location")
    data object ParentChat : Route("parent/chat")
    data object ParentNotifications : Route("parent/notifications")
    data object ParentMypage : Route("parent/mypage")
    data object ParentEditProfile : Route("parent/editprofile")
    data object ParentActivities : Route("parent/activities")
    data object ParentChildAccount : Route("parent/childaccount")
    data object ParentWithdraw : Route("parent/withdraw")
    data object ParentAppControl : Route("parent/appcontrol")
    data object ParentScreenshot : Route("parent/screenshot")
    data object ParentCameraRequest : Route("parent/camera")
    data object ParentChildProfile : Route("parent/profile")
    data object ParentHarmful : Route("parent/harmful")
    data object ParentPairing : Route("parent/pairing")
    data object ParentCommunity : Route("parent/community")
    data object ParentCommunityDetail : Route("parent/community/detail/{postId}") {
        fun create(postId: String) = "parent/community/detail/$postId"
    }
    data object ParentCommunityWrite : Route("parent/community/write")

    /** 글쓰기 화면을 그대로 쓰되 기존 글을 채워 넣는다(PATCH). */
    data object ParentCommunityEdit : Route("parent/community/edit/{postId}") {
        fun create(postId: String) = "parent/community/edit/$postId"
    }
    data object ParentNotice : Route("parent/notice")
    data object ParentContact : Route("parent/contact")
    data object ParentPay : Route("parent/pay")
    data object ParentCards : Route("parent/cards")
    data object ParentGeofence : Route("parent/geofence")
    data object ParentAppUsageDetail : Route("parent/usage-detail")
    data object ParentTerms : Route("parent/terms")

    data object ChildGraph : Route("child")
    data object ChildStart : Route("child/start")
    data object ChildOnboarding : Route("child/onboarding")
    data object ChildHome : Route("child/home")
    data object ChildChat : Route("child/chat")
    data object ChildServices : Route("child/services")
    data object ChildCamera : Route("child/camera")
    data object ChildCameraRequest : Route("child/camera?requestId={requestId}&gallery={gallery}") {
        fun create(requestId: String) = "child/camera?requestId=$requestId&gallery=false"

        /** 마이페이지 「카메라 갤러리」처럼 갤러리 탭을 바로 열 때. */
        fun gallery() = "child/camera?gallery=true"

        /** 하단 카메라 탭. 갤러리로 열린 적이 있어도 항상 카메라로 시작한다. */
        fun camera() = "child/camera?gallery=false"
    }
    data object ChildMypage : Route("child/mypage")
    data object ChildScreenshot : Route("child/screenshot")
    data object ChildPay : Route("child/pay")
    data object ChildProfileEdit : Route("child/profile/edit")
    data object ChildPairing : Route("child/pairing")
    data object ChildTerms : Route("child/terms")
    data object ChildNotice : Route("child/notice")
}

data class TabItem(
    val route: Route,
    val label: String,
    val emoji: String,
)

val ParentTabs = listOf(
    TabItem(Route.ParentDashboard, "대시보드", "📊"),
    TabItem(Route.ParentLocation, "위치", "📍"),
    TabItem(Route.ParentChat, "채팅", "💬"),
    TabItem(Route.ParentNotifications, "알림", "🔔"),
    TabItem(Route.ParentMypage, "마이", "👤"),
)

val ChildTabs = listOf(
    TabItem(Route.ChildHome, "홈", "🏠"),
    TabItem(Route.ChildChat, "채팅", "💬"),
    TabItem(Route.ChildServices, "서비스", "🛡️"),
    TabItem(Route.ChildCamera, "카메라", "📷"),
    TabItem(Route.ChildMypage, "마이", "👤"),
)
