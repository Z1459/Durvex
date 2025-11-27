package openpass.security.ui.theme

import android.app.Activity
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// 1. --- COLOR DEFINITIONS ---

val PrimaryBlue = Color(0xFF0D47A1) // A strong, secure blue
val SecondaryBlue = Color(0xFF1565C0)
val AccentGreen = Color(0xFF2E7D32) // For success states

val LightBackground = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)
val LightText = Color(0xFF212121)

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkText = Color(0xFFE0E0E0)


// 2. --- COLOR SCHEMES ---

private val DarkColorScheme = darkColorScheme(
    primary = SecondaryBlue,
    secondary = AccentGreen,
    tertiary = AccentGreen,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkText,
    onSurface = DarkText,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = AccentGreen,
    tertiary = AccentGreen,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightText,
    onSurface = LightText,
)

// 3. --- TYPOGRAPHY ---

val Typography = androidx.compose.material3.Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Define other text styles here if needed */
)


// 4. --- THEME COMPOSABLE ---

@Composable
fun OpenPassTheme(
    darkTheme: Boolean, // Explicitly passed from ViewModel
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        ) {
        val customSwitchColors = SwitchDefaults.colors(
            checkedThumbColor = colorScheme.primary,
            checkedTrackColor = colorScheme.secondary,
            uncheckedThumbColor = colorScheme.onSurface.copy(alpha = 0.6f),
            uncheckedTrackColor = colorScheme.surfaceVariant,
            )
        val customFabColors = FloatingActionButtonDefaults.containerColor
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            ) {
            content()
        }
    }
}
