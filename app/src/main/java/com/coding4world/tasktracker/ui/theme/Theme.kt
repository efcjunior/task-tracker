package com.coding4world.tasktracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CustomPrimaryDark,
    secondary = CustomSecondaryDark,
    background = CustomBackgroundDark,
    surface = CustomSurfaceDark,
    onPrimary = CustomOnPrimaryDark,
    onSecondary = CustomOnSecondaryDark,
    onBackground = CustomOnBackgroundDark,
    onSurface = CustomOnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = CustomPrimaryLight,
    secondary = CustomSecondaryLight,
    background = CustomBackgroundLight,
    surface = CustomSurfaceLight,
    onPrimary = CustomOnPrimaryLight,
    onSecondary = CustomOnSecondaryLight,
    onBackground = CustomOnBackgroundLight,
    onSurface = CustomOnSurfaceLight
)

@Composable
fun TaskTrackerAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Mantenha false para garantir que a paleta de cores personalizada seja usada
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
        typography = Typography,
        content = content
    )
}
