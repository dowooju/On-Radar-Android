package com.onradar.todak.ui.senior.check

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.onradar.todak.core.designsystem.ChoiceButton
import com.onradar.todak.core.designsystem.TodakDimens
import com.onradar.todak.core.designsystem.ScreenTitle
import com.onradar.todak.core.designsystem.SessionBadge
import com.onradar.todak.core.designsystem.TodakColors
import com.onradar.todak.core.designsystem.TodakScaffold
import com.onradar.todak.data.model.CheckRequest
import com.onradar.todak.data.model.ConditionScore
import com.onradar.todak.data.model.Session
import com.onradar.todak.data.repository.ApiException
import com.onradar.todak.data.repository.TodakRepository
import com.onradar.todak.ui.common.LoadingMessage
import com.onradar.todak.ui.common.appContainer
import com.onradar.todak.ui.common.todakViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 한 화면에 질문 하나 (유저 플로우, 11/21 피드백 반영).
 * 인사 화면 없이 바로 질문: 아침은 몸 상태, 저녁은 밥. 제출 후 짧은 대화로 이어집니다.
 */
enum class CheckStep { QUESTION, SUBMITTING }

data class CheckUiState(
    val step: CheckStep = CheckStep.QUESTION,
    val error: String? = null,
    val submittedCheckId: Long? = null,
)

class CheckViewModel(
    private val session: Session,
    private val repository: TodakRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckUiState())
    val state = _state.asStateFlow()


    fun answer(value: Int) {
        val request = when (session) {
            Session.MORNING -> CheckRequest.morning(conditionScore = value)
            Session.EVENING -> CheckRequest.evening(mealCount = value)
        }
        _state.update { it.copy(step = CheckStep.SUBMITTING, error = null) }
        viewModelScope.launch {
            val result = repository.submitCheck(request).recoverCatching { e ->
                // 같은 날 같은 세션을 다시 보내면 409 → 오늘 checkId를 찾아 수정(PUT)합니다.
                if (e is ApiException && e.code == "CHECK_ALREADY_SUBMITTED") {
                    val today = repository.today().getOrThrow()
                    val id = (if (session == Session.MORNING) today.morning else today.evening).checkId
                        ?: throw e
                    repository.updateCheck(id, request).getOrThrow()
                } else {
                    throw e
                }
            }
            result
                .onSuccess { res -> _state.update { it.copy(submittedCheckId = res.checkId) } }
                .onFailure { e -> _state.update { it.copy(step = CheckStep.QUESTION, error = e.message) } }
        }
    }
}

@Composable
fun CheckScreen(
    session: Session,
    onSubmitted: (checkId: Long) -> Unit,
    onHelp: () -> Unit,
) {
    val vm = todakViewModel(key = "check-$session") { CheckViewModel(session, it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val isMorning = session == Session.MORNING

    LaunchedEffect(state.submittedCheckId) {
        state.submittedCheckId?.let(onSubmitted)
    }

    // 인사와 질문을 소리로도 읽어 줍니다. 단계가 바뀔 때 한 번만, 화면을 떠나면 멈춥니다.
    val tts = appContainer().tts
    LaunchedEffect(state.step) {
        when (state.step) {
            // 인사 화면이 없으니 질문 앞에 인사를 붙여 읽습니다.
            CheckStep.QUESTION -> tts.speak(
                if (isMorning) "좋은 아침이에요. $MORNING_QUESTION" else "좋은 저녁이에요. $EVENING_QUESTION",
            )
            CheckStep.SUBMITTING -> tts.stop()
        }
    }
    DisposableEffect(Unit) { onDispose { tts.stop() } }

    TodakScaffold(onHelpClick = { tts.stop(); onHelp() }) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SessionBadge(isMorning)
            when (state.step) {
                CheckStep.QUESTION -> {
                    // 선택지가 넘치면 이 영역만 스크롤됩니다. (Note 8 · 글꼴 1.1배 기준으로는 넘치지 않음)
                    // 안내 문구는 두지 않습니다: 문구가 자리를 차지해 오히려 버튼을 밀어내기 때문.
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isMorning) MorningQuestion(vm::answer) else EveningQuestion(vm::answer)
                        state.error?.let {
                            Text(it, color = TodakColors.Emergency, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                CheckStep.SUBMITTING -> LoadingMessage("저장하고 있어요")
            }
        }
    }
}

@Composable
private fun MorningQuestion(onAnswer: (Int) -> Unit) {
    ScreenTitle(MORNING_QUESTION)
    // [이전]이 없어진 만큼 선택지 3개를 크게.
    val h = MORNING_CHOICE_HEIGHT
    ChoiceButton("😊", "좋아요", TodakColors.Good, TodakColors.GoodContainer, { onAnswer(ConditionScore.GOOD) }, minHeight = h)
    ChoiceButton("😐", "그저 그래요", TodakColors.SoSo, TodakColors.SoSoContainer, { onAnswer(ConditionScore.SO_SO) }, minHeight = h)
    ChoiceButton("😣", "안 좋아요", TodakColors.Bad, TodakColors.BadContainer, { onAnswer(ConditionScore.BAD) }, minHeight = h)
}

@Composable
private fun EveningQuestion(onAnswer: (Int) -> Unit) {
    ScreenTitle(EVENING_QUESTION)
    // 밥그릇 개수로 횟수를 표시합니다. 가로로 긴 버튼 4개를 84dp 높이로 맞춰 한 화면에 넣습니다.
    val h = EVENING_CHOICE_HEIGHT
    ChoiceButton("🍚", "한 번", TodakColors.Evening, TodakColors.EveningContainer, { onAnswer(1) }, minHeight = h, compact = true)
    ChoiceButton("🍚🍚", "두 번", TodakColors.Evening, TodakColors.EveningContainer, { onAnswer(2) }, minHeight = h, compact = true)
    ChoiceButton("🍚🍚🍚", "세 번", TodakColors.Evening, TodakColors.EveningContainer, { onAnswer(3) }, minHeight = h, compact = true)
    ChoiceButton("🥣", "못 먹었어요", TodakColors.OnSurfaceMuted, TodakColors.Surface, { onAnswer(0) }, minHeight = h, compact = true)
}

private const val MORNING_QUESTION = "오늘 몸은 어떠세요?"
private const val EVENING_QUESTION = "오늘 밥은 몇 번 드셨어요?"
private val MORNING_CHOICE_HEIGHT = 116.dp
private val EVENING_CHOICE_HEIGHT = 84.dp // 4개 + 질문이 Note 8(글꼴 1.1배) 한 화면에 들어오는 최대 높이
