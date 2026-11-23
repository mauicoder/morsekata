package dev.mauicoder.morsekata.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val MorseKataPrimary = Color(0xFF618EE9)

private val DarkColorScheme = darkColorScheme(
    primary = MorseKataPrimary,
    secondary = Color(0xFF8EAEE8),
    tertiary = Color(0xFF79C0C8)
)

private val LightColorScheme = lightColorScheme(
    primary = MorseKataPrimary,
    secondary = Color(0xFF4A6FA5),
    tertiary = Color(0xFF3E7B84)
)

@Composable
fun MorseKataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
