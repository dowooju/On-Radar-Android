package com.onradar.todak.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onradar.todak.BuildConfig

private val ButtonShape = RoundedCornerShape(20.dp)

/**
 * 모든 화면의 기본 틀. 내용 아래에 [도와주세요] 바를 항상 같은 자리에 고정합니다.
 * (심사 피드백: 대화 화면에서도 보여야 함)
 */
@Composable
fun TodakScaffold(
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
    showHelpBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        if (BuildConfig.USE_MOCK) DemoModeBanner()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = TodakDimens.ScreenPadding, vertical = 16.dp),
        ) {
            content()
        }
        if (showHelpBar) HelpBar(onClick = onHelpClick)
    }
}

/** 가짜 데이터로 동작 중임을 모든 화면 맨 위에 알립니다. 실제 전송으로 오해하지 않게. */
@Composable
private fun DemoModeBanner() {
    Text(
        text = "🧪 시연 모드 · 실제로 전송되지 않아요",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
        maxLines = 1,
        color = TodakColors.OnSurfaceMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(TodakColors.Outline)
            .padding(vertical = 6.dp),
    )
}

/** 하단 고정 [도와주세요] 바. 응급 전용 빨강. */
@Composable
fun HelpBar(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TodakDimens.HelpBarHeight)
            .semantics { contentDescription = "도와주세요. 누르면 복지사 선생님께 알리고 119 전화 화면을 엽니다." },
        shape = RoundedCornerShape(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TodakColors.Emergency,
            contentColor = TodakColors.OnEmergency,
        ),
    ) {
        Text("🆘  도와주세요", style = MaterialTheme.typography.labelLarge)
    }
}

/** 주요 행동 버튼 (초록). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = TodakColors.Primary,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TodakDimens.LargeButtonHeight),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = TodakColors.OnPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(text, style = textStyle, textAlign = TextAlign.Center)
    }
}

/** 보조 버튼 ([이전], [다음에 할게요] 등). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TodakDimens.ButtonMinHeight),
        shape = ButtonShape,
        border = BorderStroke(2.dp, TodakColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = TodakColors.Surface,
            contentColor = TodakColors.OnSurface,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

/**
 * 선택지 버튼. 글자 + 그림(이모지) + 색 세 가지로 표시합니다.
 * 글을 잘 읽지 못해도 표정과 색으로 고를 수 있게.
 */
@Composable
fun ChoiceButton(
    emoji: String,
    label: String,
    color: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = TodakDimens.ChoiceButtonHeight,
    compact: Boolean = false, // true면 그림을 줄이고 위아래 여백을 줄여 72dp 높이에 맞춤
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        shape = ButtonShape,
        border = BorderStroke(3.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = TodakColors.OnSurface,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = if (compact) 2.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = if (compact) 26.sp else 36.sp, lineHeight = if (compact) 30.sp else 44.sp)
            Spacer(Modifier.width(20.dp))
            Text(label, style = MaterialTheme.typography.titleLarge, color = color)
        }
    }
}

/** 화면 상단의 큰 질문/안내 문구. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.headlineLarge, color = TodakColors.OnSurface)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = TodakColors.OnSurfaceMuted)
        }
    }
}

/** 아침/저녁 표시 배지. */
@Composable
fun SessionBadge(isMorning: Boolean) {
    val (text, color, container) = if (isMorning) {
        Triple("☀️ 아침", TodakColors.OnMorningContainer, TodakColors.MorningContainer)
    } else {
        Triple("🌙 저녁", TodakColors.Evening, TodakColors.EveningContainer)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 6.dp),
    )
}
