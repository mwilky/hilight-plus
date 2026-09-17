@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mwilky.hilight.plus.Licensing
import com.mwilky.hilight.plus.R

/**
 * Fixed green palette so the card stands apart from the wallpaper-driven status cards in
 * both themes. Light: soft mint wash with deep green text. Dark: deep green with mint text.
 */
private data class LicenseColors(val accent: Color, val onAccent: Color, val container: Color, val content: Color)

private val LightGreen = LicenseColors(
    accent = Color(0xFF1E7B44),
    onAccent = Color(0xFFFFFFFF),
    container = Color(0xFFDDF2E1),
    content = Color(0xFF0F3D22)
)

private val DarkGreen = LicenseColors(
    accent = Color(0xFF6FD08F),
    onAccent = Color(0xFF00391A),
    container = Color(0xFF12301F),
    content = Color(0xFFC6EBD1)
)

/**
 * Trial / purchase state in the same shape as the diagnostic cards. Green while the trial runs
 * or once purchased, error once the trial has ended. Shown in onboarding and, until purchased,
 * at the top of Home.
 */
@Composable
fun LicenseCard(
    status: Licensing.Status,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.colorScheme
    val green = if (isSystemInDarkTheme()) DarkGreen else LightGreen
    val daysLeft = status.trialDaysLeft
    val price = status.priceText

    val title: String
    val statusText: String
    val subtitle: String
    val colors: LicenseColors
    when {
        status.purchased -> {
            title = stringResource(R.string.license_title_unlocked)
            statusText = stringResource(R.string.license_status_unlocked)
            subtitle = stringResource(R.string.license_desc_unlocked)
            colors = green
        }
        status.trialExpired -> {
            title = stringResource(R.string.license_title_expired)
            statusText = stringResource(R.string.license_status_expired)
            subtitle = stringResource(R.string.license_desc_expired)
            colors = LicenseColors(
                accent = c.error,
                onAccent = c.onError,
                container = c.errorContainer.copy(alpha = 0.45f),
                content = c.onErrorContainer
            )
        }
        else -> {
            title = stringResource(R.string.license_title_trial)
            if (daysLeft == null) {
                statusText = stringResource(R.string.license_status_pending)
                subtitle = stringResource(R.string.license_desc_pending, Licensing.TRIAL_DAYS)
            } else {
                statusText = pluralStringResource(R.plurals.license_days_left, daysLeft, daysLeft)
                subtitle = if (price != null) {
                    stringResource(R.string.license_desc_trial_price, price)
                } else {
                    stringResource(R.string.license_desc_trial)
                }
            }
            colors = green
        }
    }

    ExpressiveStatusCard(
        title = title,
        subtitle = subtitle,
        icon = Icons.Rounded.WorkspacePremium,
        statusText = statusText,
        accentColor = colors.accent,
        containerColor = colors.container,
        contentColor = colors.content,
        modifier = modifier,
        bottomAction = if (status.purchased) null else {
            {
                Button(
                    onClick = onBuy,
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent
                    )
                ) {
                    Text(
                        if (price != null) stringResource(R.string.license_btn_buy_price, price)
                        else stringResource(R.string.license_btn_buy)
                    )
                }
            }
        }
    )
}
