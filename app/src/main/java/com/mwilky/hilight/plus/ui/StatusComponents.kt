@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One semantic status treatment: OK uses primary, not-OK uses error.
 * LED / rule colours stay on the domain objects, not here.
 */
@Composable
fun StandardDiagnosticCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    statusText: String,
    isOk: Boolean,
    modifier: Modifier = Modifier,
    bottomAction: (@Composable () -> Unit)? = null
) {
    val accentColor = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val containerColor = if (isOk) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
    }
    val contentColor = if (isOk) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    ExpressiveStatusCard(
        title = title,
        subtitle = subtitle,
        icon = icon,
        statusText = statusText,
        accentColor = accentColor,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier,
        bottomAction = bottomAction
    )
}

@Composable
fun ExpressiveStatusCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    statusText: String,
    accentColor: Color,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    bottomAction: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = HiLightTheme.CardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )

                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.85f)
            )

            if (bottomAction != null) {
                HorizontalDivider(
                    color = accentColor.copy(alpha = 0.15f),
                    thickness = 1.dp
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    bottomAction()
                }
            }
        }
    }
}
