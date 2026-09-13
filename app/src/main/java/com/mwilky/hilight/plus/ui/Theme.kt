package com.mwilky.hilight.plus.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object HiLightTheme {
    val PaletteSwatch = 40.dp
    val RuleRowPadding = 10.dp
    val OnboardingDot = 10.dp
    val OnboardingDotSelected = 12.dp

    val CardShape @Composable get() = MaterialTheme.shapes.extraLarge
    val SectionShape @Composable get() = MaterialTheme.shapes.extraLarge
    val DialogCardShape @Composable get() = MaterialTheme.shapes.large
    val PillShape = RoundedCornerShape(100.dp)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
