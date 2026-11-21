package com.onradar.todak.data.repository

import com.onradar.todak.data.model.CheckRequest
import com.onradar.todak.data.model.CheckResponse
import com.onradar.todak.data.model.CheckStatus
import com.onradar.todak.data.model.ConditionScore
import com.onradar.todak.data.model.ConversationTurnResponse
import com.onradar.todak.data.model.EmergencyResponse
import com.onradar.todak.data.model.RegisterResponse
import com.onradar.todak.data.model.RiskLevel
import com.onradar.todak.data.model.Session
import com.onradar.todak.data.model.SessionState
import com.onradar.todak.data.model.TodayResponse
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 백엔드 없이 화면을 개발·시연하기 위한 가짜 저장소.
 * 응답 모양은 API 명세서 (2)의 JSON 예시를 그대로 따릅니다.
 */
class FakeTodakRepository : TodakRepository {

    private val kst = ZoneOffset.ofHours(9)
    private var nextCheckId = 101L
    private var morning = SessionState(CheckStatus.PENDING)
    private var evening = SessionState(CheckStatus.PENDING)
    private var lastCheck: CheckRequest? = null
    private var turn = 0

    override suspend fun register(registrationCode: String, fcmToken: String?): Result<RegisterResponse> {
        delay(400)
        return Result.success(RegisterResponse(1, "dvc_fake_token", "김순자", "할머니"))
    }

    override suspend fun today(): Result<TodayResponse> {
        delay(300)
        // 체크 시간 (11/21 팀 확정): 아침 08:00~11:00, 저녁 18:00~21:00.
        // 실서버는 시간이 지나면 status=MISSED로 내려줍니다. 시연 모드는 언제든 흐름을 볼 수 있게 PENDING으로 둡니다.
        val eveningOpensAt = OffsetDateTime.of(LocalDate.now(), LocalTime.of(18, 0), kst).toString()
        return Result.success(
            TodayResponse(
                date = LocalDate.now().toString(),
                morning = morning,
                evening = if (evening.status == CheckStatus.PENDING) evening.copy(opensAt = eveningOpensAt) else evening,
                callContact = null,
            ),
        )
    }

    override suspend fun submitCheck(request: CheckRequest): Result<CheckResponse> {
        delay(400)
        val now = OffsetDateTime.now(kst).withNano(0).toString()
        val id = nextCheckId++
        val done = SessionState(CheckStatus.DONE, checkId = id, respondedAt = now)
        if (request.session == Session.MORNING) morning = done else evening = done
        lastCheck = request
        return Result.success(CheckResponse(id, now, RiskLevel.NORMAL))
    }

    override suspend fun updateCheck(checkId: Long, request: CheckRequest): Result<CheckResponse> {
        delay(300)
        lastCheck = request
        return Result.success(CheckResponse(checkId, OffsetDateTime.now(kst).withNano(0).toString(), RiskLevel.NORMAL))
    }

    override suspend fun startConversation(checkId: Long): Result<ConversationTurnResponse> {
        delay(1200) // AI가 답을 준비하는 시간 흉내
        turn = 1
        return Result.success(ConversationTurnResponse(55, firstLine(lastCheck), turn, ended = false))
    }

    override suspend fun sendMessage(conversationId: Long, text: String): Result<ConversationTurnResponse> {
        delay(1500)
        turn++
        val ended = turn >= MAX_TURNS
        val reply = if (ended) {
            "말씀 나눠 주셔서 고마워요. 오늘도 편안한 하루 보내세요."
        } else {
            "그러셨군요. 이야기해 주셔서 고마워요. 오늘은 누구랑 이야기 나누셨어요?"
        }
        return Result.success(ConversationTurnResponse(null, reply, turn, ended))
    }

    override suspend fun endConversation(conversationId: Long): Result<Unit> {
        delay(200)
        return Result.success(Unit)
    }

    override suspend fun emergency(): Result<EmergencyResponse> {
        delay(400)
        // 실제로 보내지 않으므로 "알렸어요" 같은 성공 문구를 쓰지 않습니다.
        return Result.success(EmergencyResponse(9, "EMERGENCY", "시연 모드라 복지사 선생님께 실제로 알리지는 않았어요"))
    }

    // 말동무 말투 원칙: 탓하지 않고, 걱정·공감한 뒤 질문 하나. 좋은 날은 칭찬.
    private fun firstLine(check: CheckRequest?): String = when {
        check?.session == Session.MORNING && check.conditionScore == ConditionScore.BAD ->
            "오늘 몸이 좀 안 좋으시구나. 어디가 제일 불편하세요?"
        check?.session == Session.MORNING && check.conditionScore == ConditionScore.SO_SO ->
            "오늘은 그저 그러시군요. 밤에 잘 주무셨어요?"
        check?.session == Session.MORNING ->
            "몸이 괜찮으시다니 다행이에요! 오늘은 뭐 하실 계획이세요?"
        check?.mealCount == 0 ->
            "오늘은 식사를 못 하셨네요. 입맛이 별로 없으셨어요?"
        (check?.mealCount ?: 3) < 3 ->
            "오늘은 ${check?.mealCount}번 드셨네요. 입맛이 별로 없으셨어요?"
        else ->
            "세 끼 잘 챙겨 드셨네요! 오늘 하루는 어떠셨어요?"
    }

    private companion object {
        const val MAX_TURNS = 3
    }
}
