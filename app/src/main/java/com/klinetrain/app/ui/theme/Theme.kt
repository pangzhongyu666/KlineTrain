package com.klinetrain.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌色：参考截图的紫色系
val Purple = Color(0xFF5B4FE9)
val PurpleDark = Color(0xFF2B2160)
val PurpleDeep = Color(0xFF171233)
val UpRed = Color(0xFFE83C3C)       // A股红涨
val DownGreen = Color(0xFF0FA97D)   // A股绿跌
val GoldYellow = Color(0xFFF5A623)
val BgLight = Color(0xFFF5F5FA)

private val LightColors = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    secondary = GoldYellow,
    background = BgLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0F6),
    error = UpRed
)

private val DarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    secondary = GoldYellow,
    background = PurpleDeep,
    surface = Color(0xFF221C45),
    error = UpRed
)

@Composable
fun KlineTrainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
