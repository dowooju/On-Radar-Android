package com.onradar.todak.ui.senior.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onradar.todak.core.designsystem.PrimaryButton
import com.onradar.todak.core.designsystem.SecondaryButton
import com.onradar.todak.core.designsystem.TodakColors
import com.onradar.todak.core.designsystem.TodakScaffold
import com.onradar.todak.core.speech.SpeechInput
import com.onradar.todak.ui.common.todakViewModel

@Composable
fun ChatScreen(
    checkId: Long,
    onFinished: () -> Unit,
    onHelp: () -> Unit,
) {
    val vm = todakViewModel(key = "chat-$checkId") { ChatViewModel(checkId, it.repository, it.tts, it.appScope) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val speech = remember {
        SpeechInput(
            context = context,
            onListening = vm::onListening,
            onResult = vm::send,
            onError = { error ->
                vm.onMicUnavailable(
                    when (error) {
                        SpeechInput.SpeechError.NOT_AVAILABLE -> "이 폰에서는 목소리 듣기를 쓸 수 없어요"
                        // 설정은 복지사가 도와드리는 것이 전제라, 어르신께는 짧게만 알립니다. (자세한 방법은 README)
                        SpeechInput.SpeechError.NEED_GOOGLE_APP -> "목소리 듣기가 꺼져 있어요"
                        SpeechInput.SpeechError.NO_MATCH -> "목소리를 잘 못 들었어요"
                        SpeechInput.SpeechError.NETWORK -> "인터넷이 연결되지 않아 목소리를 들을 수 없어요"
                        SpeechInput.SpeechError.OTHER -> "목소리 듣기가 잠깐 안 돼요"
                    },
                )
            },
        )
    }
    DisposableEffect(Unit) {
        vm.onMicTimeout = speech::release
        onDispose {
            vm.onMicTimeout = null
            speech.release()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start() else vm.onMicUnavailable("마이크를 쓸 수 없어요")
    }
    val startListening = {
        // 누른 즉시 "마이크를 준비하고 있어요"를 보여 주고, 준비 중에 또 눌러도 다시 시작하지 않습니다.
        if (vm.onMicPressed()) {
            if (hasMicPermission(context)) speech.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // 대화 화면에 들어오자마자 마이크 권한을 물어 둡니다. 그래야 AI 질문이 끝났을 때 버튼 없이 바로 들을 수 있어요.
    // (거절하면 자동 듣기는 하지 않고, [누르고 말씀하세요]를 누를 때 다시 묻습니다.)
    val entryPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!hasMicPermission(context)) entryPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // AI가 질문을 다 읽으면 자동으로 듣기 시작. 말을 마치면 [다 말했어요] (또는 잠시 조용하면 자동 종료).
    LaunchedEffect(state.autoListenTick) {
        if (state.autoListenTick > 0 && state.phase == ChatPhase.READY && hasMicPermission(context)) {
            startListening()
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == ChatPhase.FINISHED) onFinished()
    }

    // [도와주세요]는 대화 화면에서도 같은 자리에 있습니다. (심사 피드백)
    TodakScaffold(onHelpClick = { vm.stopSpeaking(); speech.release(); onHelp() }) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🤖 AI 말동무와 이야기", style = MaterialTheme.typography.bodyLarge, color = TodakColors.OnSurfaceMuted)

            val listState = rememberLazyListState()
            LaunchedEffect(state.messages.size) {
                if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.messages) { Bubble(it) }
            }

            StatusArea(state, onSpeak = startListening, onStopListening = speech::stop, onRetry = vm::retryLast)
            SecondaryButton("다음에 할게요", onClick = vm::skip)
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromAi) Arrangement.Start else Arrangement.End,
    ) {
        Text(
            text = message.text,
            // 큰 자막: AI 말은 화면에 크게 보여 주고 소리로도 읽습니다.
            style = if (message.fromAi) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth(if (message.fromAi) 1f else 0.85f)
                .background(
                    if (message.fromAi) TodakColors.Surface else TodakColors.GoodContainer,
                    RoundedCornerShape(20.dp),
                )
                .padding(20.dp),
        )
    }
}

@Composable
private fun StatusArea(
    state: ChatUiState,
    onSpeak: () -> Unit,
    onStopListening: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state.phase) {
        ChatPhase.PREPARING -> StatusLine("💭 답변을 준비하고 있어요", showSpinner = true)
        ChatPhase.AI_SPEAKING -> StatusLine("🔊 말하고 있어요")
        ChatPhase.READY -> PrimaryButton("🎤 누르고 말씀하세요", onClick = onSpeak)
        ChatPhase.MIC_PREPARING -> StatusLine("🎤 마이크를 준비하고 있어요\n잠깐만 기다려 주세요", showSpinner = true)
        ChatPhase.LISTENING -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusLine("👂 듣고 있어요. 말씀하세요", showSpinner = true)
            PrimaryButton("다 말했어요", onClick = onStopListening, containerColor = TodakColors.Evening)
        }
        // 마이크가 안 될 때: 질문과 상관없는 공통 답을 대신 보내지 않고, 다시 말하기 / 다음에 할게요만 둡니다.
        ChatPhase.MIC_FAILED -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusLine(state.failReason ?: "목소리를 잘 못 들었어요")
            PrimaryButton("🎤 다시 말하기", onClick = onSpeak)
        }
        ChatPhase.ERROR -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusLine(state.failReason ?: "지금은 이야기를 이어가기 어려워요")
            PrimaryButton("다시 해 보기", onClick = onRetry)
        }
        ChatPhase.FINISHED -> Unit
    }
}

@Composable
private fun StatusLine(text: String, showSpinner: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TodakColors.MorningContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showSpinner) CircularProgressIndicator(Modifier.size(32.dp), color = TodakColors.Primary, strokeWidth = 4.dp)
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
