package com.hack.kidhack.feature.child.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.BrandGradientHeader
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.feature.parent.formatChatSentAt
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChildChatScreen(
    viewModel: ChildChatViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val listState = rememberLazyListState()

    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResume()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error
        // 방 로드 실패는 화면에 다시 시도를 보여 주므로 토스트만 띄우고 clear 하지 않음
        if (message != null && state.room != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .imePadding(),
    ) {
        // 채팅은 메시지 세로 공간이 중요하다 — 히어로 헤더의 40dp 아래 여백을 슬림하게 줄인다.
        BrandGradientHeader(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("👩", fontSize = 20.sp)
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.child_chat_mom),
                        color = Color.White,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 16.sp,
                    )
                    // 서버 ChatRoomSummaryResponse 에 접속 여부가 없어 온라인 표시를 걷어냈다.
                    // 실제로 주는 값은 안 읽음 수라 그것을 보여 준다.
                    val unread = state.room?.unreadCount ?: 0
                    if (unread > 0) {
                        Text(
                            text = stringResource(R.string.child_chat_unread, unread),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        if (!state.loading && state.room == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error ?: stringResource(R.string.child_chat_empty),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "다시 시도",
                        color = colors.brand,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable(onClick = viewModel::load),
                    )
                }
            }
        } else {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.brand)
                    }
                }
            }
            if (!state.loading && state.messages.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.child_chat_empty),
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 12.sp,
                    )
                }
            }
            items(state.messages, key = { it.id }) { message ->
                ChatBubble(message)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = state.draft,
                onValueChange = viewModel::onDraft,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                textStyle = TextStyle(
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 14.sp,
                    color = colors.text,
                ),
                cursorBrush = SolidColor(colors.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
                decorationBox = { inner ->
                    if (state.draft.isEmpty()) {
                        Text(
                            text = stringResource(R.string.child_chat_hint),
                            color = colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 14.sp,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (state.canSend) colors.brand else colors.border)
                    .clickable(enabled = state.canSend, onClick = viewModel::send),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.child_chat_send),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                )
            }
        }
        } // end room-ready branch
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val colors = LocalKidhackColors.current
    val mine = message.fromRole == Role.CHILD
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.brandLight),
                contentAlignment = Alignment.Center,
            ) {
                Text("👩", fontSize = 14.sp)
            }
            Spacer(Modifier.size(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (mine) Modifier.background(colors.brand)
                    else Modifier
                        .background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp)),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.text,
                color = if (mine) Color.White else colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatChatSentAt(message.sentAt),
                color = if (mine) Color.White.copy(alpha = 0.75f) else colors.muted,
                fontSize = 12.sp,
            )
        }
    }
}
