package com.woongpapa.weathercodi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 날씨코디 디자인 토큰 */
object Codi {
    val Bg = Color(0xFFF4F5F9)
    val Card = Color.White
    val Ink = Color(0xFF14182B)
    val Sub = Color(0xFF8B91A3)
    val Line = Color(0xFFE6E8EF)
    val Primary = Color(0xFF3B5BFD)
    val PrimarySoft = Color(0xFFE8ECFF)
    val Accent = Color(0xFFFF7A59)
    val AccentSoft = Color(0xFFFFEEE8)
    val Field = Color(0xFFEFF1F6)

    val CardShape = RoundedCornerShape(22.dp)
    val TileShape = RoundedCornerShape(16.dp)
    val PillShape = RoundedCornerShape(50)
}

@Composable
fun CodiTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Codi.Primary, onPrimary = Color.White,
            primaryContainer = Codi.PrimarySoft, onPrimaryContainer = Codi.Primary,
            secondary = Codi.Accent, onSecondary = Color.White,
            secondaryContainer = Codi.AccentSoft, onSecondaryContainer = Codi.Accent,
            background = Codi.Bg, onBackground = Codi.Ink,
            surface = Codi.Bg, onSurface = Codi.Ink,
            surfaceVariant = Codi.Field, onSurfaceVariant = Codi.Sub,
            surfaceContainer = Codi.Card, surfaceContainerLow = Codi.Card,
            surfaceContainerHigh = Codi.Card, surfaceContainerHighest = Codi.Field,
            outline = Codi.Sub, outlineVariant = Codi.Line,
            error = Color(0xFFE5484D),
        ),
        typography = base.copy(
            headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, color = Codi.Ink),
            headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Codi.Sub),
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}

/** 화면 상단 큰 제목 */
@Composable
fun ScreenHeader(title: String, caption: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 12.dp, bottom = 4.dp)) {
        caption?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Codi.Sub) }
        Text(title, style = MaterialTheme.typography.headlineLarge)
    }
}

/** 흰 카드 (얇은 테두리, 그림자 없음) */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Int = 18,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clip(Codi.CardShape).clickable(onClick = onClick) else Modifier),
        shape = Codi.CardShape,
        color = Codi.Card,
        border = BorderStroke(1.dp, Codi.Line),
    ) {
        Column(Modifier.padding(padding.dp), content = content)
    }
}

/** 작은 알약 라벨 */
@Composable
fun Pill(text: String, bg: Color = Codi.Field, fg: Color = Codi.Ink, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(Codi.PillShape).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium,
    )
}

/** 큰 기본 버튼 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Codi.Ink, contentColor = Color.White,
            disabledContainerColor = Codi.Line, disabledContentColor = Codi.Sub),
        elevation = null,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** 단계 번호 동그라미 */
@Composable
fun NumberDot(text: String, done: Boolean) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(if (done) Codi.Primary else Codi.Field),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (done) "✓" else text, color = if (done) Color.White else Codi.Sub,
            fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** 날씨별 배경 그라데이션 */
fun weatherBrush(summary: String, rain: Boolean, snow: Boolean): Brush = Brush.linearGradient(
    when {
        snow -> listOf(Color(0xFF7FA8D9), Color(0xFFB9D3F0))
        rain -> listOf(Color(0xFF3E4C6B), Color(0xFF6B7FA3))
        summary == "흐림" -> listOf(Color(0xFF5E6B82), Color(0xFF98A4B8))
        summary == "구름많음" -> listOf(Color(0xFF4A78E0), Color(0xFF8FB1F2))
        else -> listOf(Color(0xFF2F6BFF), Color(0xFF5FB0FF))
    }
)

/** 옷걸이 아이콘 (Material 'Checkroom' 모양) */
val HangerIcon: ImageVector by lazy {
    ImageVector.Builder("hanger", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(
            "M21.6,18.2L13,11.75v-0.91c1.65,-0.49 2.8,-2.17 2.43,-4.05c-0.26,-1.31 -1.3,-2.4 -2.61,-2.7" +
                "C10.54,3.57 8.5,5.3 8.5,7.5h2c0,-0.83 0.67,-1.5 1.5,-1.5s1.5,0.67 1.5,1.5c0,0.84 -0.69,1.52 -1.53,1.5" +
                "c-0.54,-0.01 -0.97,0.45 -0.97,0.99v1.76L2.4,18.2C1.63,18.78 2.04,20 3,20h18c0.96,0 1.37,-1.22 0.6,-1.8z" +
                "M6,18l6,-4.5l6,4.5H6z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** 입력칸 공통 스타일 */
@Composable
fun codiFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
    unfocusedBorderColor = Codi.Line, focusedBorderColor = Codi.Primary,
)

val FieldShape = RoundedCornerShape(14.dp)
