package com.mwilky.hilight.plus.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object HiLightTheme {
    val CardShape = RoundedCornerShape(24.dp)
    val SectionShape = RoundedCornerShape(28.dp)
    val PillShape = RoundedCornerShape(100.dp)
    val DialogCardShape = RoundedCornerShape(16.dp)
    val PaletteSwatch = 40.dp
    val RuleRowPadding = 10.dp
    val OnboardingDot = 10.dp
    val OnboardingDotSelected = 12.dp
}

@Composable
fun HiLightPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
