package com.onradar.todak.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onradar.todak.data.model.Session
import com.onradar.todak.ui.common.appContainer
import com.onradar.todak.ui.onboarding.OnboardingScreen
import com.onradar.todak.ui.senior.chat.ChatScreen
import com.onradar.todak.ui.senior.check.CheckScreen
import com.onradar.todak.ui.senior.emergency.EmergencyScreen
import com.onradar.todak.ui.senior.home.HomeScreen

/**
 * 화면 흐름 (유저 플로우, 11/21):
 * 등록 → 홈 → 몸 상태(아침) / 인사 → 밥(저녁) → 짧은 대화 → 홈 (완료 화면 없음)
 * 모든 화면 하단 [도와주세요] → 복지사 즉시 알림 → 119 전화 화면
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHECK = "check/{session}"
    const val CHAT = "chat/{checkId}"
    const val EMERGENCY = "emergency"

    fun check(session: Session) = "check/${session.name}"
    fun chat(checkId: Long) = "chat/$checkId"
}

@Composable
fun TodakNavHost(navController: NavHostController = rememberNavController()) {
    val sessionStore = appContainer().sessionStore
    val start = if (sessionStore.isRegistered) Routes.HOME else Routes.ONBOARDING
    val goHelp = { navController.navigate(Routes.EMERGENCY) }
    val goHome = {
        navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
    }

    // 서버가 토큰을 거부하면(401) 복지사 도움을 받아 다시 등록하도록 등록 화면으로 보냅니다.
    val expired by sessionStore.expired.collectAsStateWithLifecycle()
    LaunchedEffect(expired) {
        if (expired) {
            navController.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(reRegister = expired, onRegistered = {
                navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }

        composable(Routes.HOME) {
            HomeScreen(
                displayName = sessionStore.displayName,
                onStartCheck = { navController.navigate(Routes.check(it)) },
                onTalk = { checkId, _ -> navController.navigate(Routes.chat(checkId)) },
                onHelp = goHelp,
            )
        }

        composable(Routes.CHECK, arguments = listOf(navArgument("session") { type = NavType.StringType })) { entry ->
            val session = Session.valueOf(entry.arguments?.getString("session") ?: Session.MORNING.name)
            CheckScreen(
                session = session,
                onSubmitted = { checkId ->
                    navController.navigate(Routes.chat(checkId)) {
                        popUpTo(Routes.HOME) // 대화에서 뒤로 가면 체크가 아니라 홈으로
                    }
                },
                onHelp = goHelp,
            )
        }

        composable(
            Routes.CHAT,
            arguments = listOf(navArgument("checkId") { type = NavType.LongType }),
        ) { entry ->
            val checkId = entry.arguments?.getLong("checkId") ?: 0L
            // 대화가 끝나면(또는 [다음에 할게요]) 완료 화면 없이 바로 홈으로. 홈이 "오늘 체크를 마쳤어요"를 보여 줍니다.
            ChatScreen(checkId = checkId, onFinished = goHome, onHelp = goHelp)
        }

        composable(Routes.EMERGENCY) {
            EmergencyScreen(onBack = { navController.popBackStack() })
        }
    }
}
