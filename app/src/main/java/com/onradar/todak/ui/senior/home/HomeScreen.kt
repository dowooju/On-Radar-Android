package com.onradar.todak.ui.senior.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.onradar.todak.BuildConfig
import com.onradar.todak.core.designsystem.PrimaryButton
import com.onradar.todak.core.designsystem.ScreenTitle
import com.onradar.todak.core.designsystem.SessionBadge
import com.onradar.todak.core.designsystem.TodakColors
import com.onradar.todak.core.designsystem.TodakScaffold
import com.onradar.todak.data.model.CheckStatus
import com.onradar.todak.data.model.Session
import com.onradar.todak.data.model.SessionState
import com.onradar.todak.data.model.TodayResponse
import com.onradar.todak.data.repository.TodakRepository
import com.onradar.todak.ui.common.LoadingMessage
import com.onradar.todak.ui.common.todakViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** 홈에서 지금 보여줄 "할 일 하나". 미응답·위험 판단은 하지 않고 서버의 status·opensAt만 따릅니다. */
sealed interface HomeAction {
    data class DoCheck(val session: Session) : HomeAction
    /** 체크를 마친 뒤: "오늘 체크를 마쳤어요" + 큰 [이야기 나누기] */
    data class Talk(val checkId: Long, val session: Session) : HomeAction
    /** 할 수 있는 체크가 아직 없음 (예: 아침을 놓쳤고 저녁은 아직 안 열림) */
    data class Wait(val nextGuide: String?) : HomeAction
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val action: HomeAction) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(private val repository: TodakRepository) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state = _state.asStateFlow()

    private var refreshJob: Job? = null

    fun load() {
        if (_state.value !is HomeUiState.Ready) _state.value = HomeUiState.Loading
        viewModelScope.launch {
            repository.today()
                .onSuccess { today ->
                    _state.value = HomeUiState.Ready(decide(today))
                    scheduleRefreshAtOpen(today)
                }
                .onFailure { _state.value = HomeUiState.Error(it.message ?: "잠시 후 다시 해 주세요") }
        }
    }

    /** 홈을 켜 둔 채 체크가 열리는 시각이 되면 다시 불러옵니다. */
    private fun scheduleRefreshAtOpen(today: TodayResponse) {
        refreshJob?.cancel()
        val next = listOf(today.morning, today.evening)
            .filter { it.status == CheckStatus.PENDING }
            .mapNotNull { parse(it.opensAt) }
            .filter { it.isAfter(OffsetDateTime.now()) }
            .minOrNull() ?: return
        refreshJob = viewModelScope.launch {
            delay(Duration.between(OffsetDateTime.now(), next).toMillis() + 1_000)
            load()
        }
    }

    private fun decide(today: TodayResponse): HomeAction {
        val morning = today.morning
        val evening = today.evening
        return when {
            isOpen(evening) -> HomeAction.DoCheck(Session.EVENING)
            evening.status == CheckStatus.DONE && evening.checkId != null ->
                HomeAction.Talk(evening.checkId, Session.EVENING)
            isOpen(morning) -> HomeAction.DoCheck(Session.MORNING)
            morning.status == CheckStatus.DONE && morning.checkId != null ->
                HomeAction.Talk(morning.checkId, Session.MORNING)
            else -> HomeAction.Wait(guide(evening))
        }
    }

    /**
     * 체크를 할 수 있는지: 서버가 PENDING이라고 하고, opensAt이 없거나 이미 지났을 때.
     * (opensAt이 없으면 이미 열린 것으로 봅니다. API 명세서: opensAt은 "아직 시간 전"일 때 옴)
     */
    private fun isOpen(s: SessionState): Boolean {
        if (s.status != CheckStatus.PENDING) return false
        val opensAt = parse(s.opensAt) ?: return true
        return !OffsetDateTime.now().isBefore(opensAt)
    }

    private fun guide(next: SessionState): String? {
        if (next.status != CheckStatus.PENDING) return null
        val opensAt = parse(next.opensAt) ?: return null
        return "다음 체크는 ${opensAt.format(KOREAN_TIME)}예요"
    }

    private fun parse(value: String?): OffsetDateTime? = try {
        value?.let(OffsetDateTime::parse)
    } catch (_: DateTimeParseException) {
        null
    }

    private companion object {
        val KOREAN_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("a h시", java.util.Locale.KOREAN)
    }
}

@Composable
fun HomeScreen(
    displayName: String,
    onStartCheck: (Session) -> Unit,
    onTalk: (checkId: Long, session: Session) -> Unit,
    onHelp: () -> Unit,
) {
    val vm = todakViewModel { HomeViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    // 홈으로 돌아오거나 앱이 다시 앞으로 올 때마다 오늘 상태를 새로 불러옵니다.
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }

    TodakScaffold(onHelpClick = onHelp) {
        when (val s = state) {
            HomeUiState.Loading -> LoadingMessage("오늘 할 일을 불러오고 있어요")
            is HomeUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ScreenTitle("불러오지 못했어요", subtitle = s.message)
                PrimaryButton("다시 해 보기", onClick = vm::load)
            }
            is HomeUiState.Ready -> HomeContent(displayName, s.action, onStartCheck, onTalk)
        }
    }
}

@Composable
private fun HomeContent(
    displayName: String,
    action: HomeAction,
    onStartCheck: (Session) -> Unit,
    onTalk: (Long, Session) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("$displayName,\n안녕하세요", style = MaterialTheme.typography.displayLarge)

        when (action) {
            is HomeAction.DoCheck -> {
                // 인사 바로 밑에 아침/저녁 표시, 그 아래 남은 화면을 [오늘 체크하기]가 꽉 채웁니다.
                SessionBadge(action.session == Session.MORNING)
                PrimaryButton(
                    "오늘 체크하기",
                    onClick = { onStartCheck(action.session) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.displayLarge,
                )
            }
            is HomeAction.Talk -> {
                // 인사 바로 밑에 '오늘 체크를 마쳤어요'만, 그 아래 남은 화면을 [이야기 나누기]가 꽉 채웁니다.
                Text(
                    "✅ 오늘 체크를 마쳤어요",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TodakColors.Done,
                )
                PrimaryButton(
                    "이야기 나누기",
                    onClick = { onTalk(action.checkId, action.session) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.displayLarge,
                )
                // 개발용: 낮에도 저녁 체크 흐름을 볼 수 있게. debug + 시연 모드에서만 보이고 release에는 없습니다.
                if (BuildConfig.DEBUG && BuildConfig.USE_MOCK && action.session == Session.MORNING) {
                    TextButton(onClick = { onStartCheck(Session.EVENING) }, modifier = Modifier.fillMaxWidth()) {
                        Text("개발용 · 저녁 체크 미리 보기", color = TodakColors.OnSurfaceMuted, fontSize = 16.sp)
                    }
                }
            }
            is HomeAction.Wait -> {
                Text(
                    action.nextGuide ?: "지금은 할 체크가 없어요",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
        // callContact(가족 전화)는 가족 기능(TODO) 전까지 항상 null이라 [딸에게 전화하기]를 그리지 않습니다.
    }
}
