@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mwilky.hilight.plus.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mwilky.hilight.plus.PauseDuration
import com.mwilky.hilight.plus.PauseFeature
import com.mwilky.hilight.plus.PauseState
import com.mwilky.hilight.plus.R
import kotlinx.coroutines.launch

/**
 * The pause sheet, opened from the Quick Settings tile or the Home pause card over whatever is
 * on screen. [onDone] runs once it has slid away, paused or not.
 */
@Composable
fun PauseSheet(onDone: () -> Unit, viewModel: PauseViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDone() }
    }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheetState) {
        state?.let { s ->
            PauseSheetContent(
                state = s,
                onToggle = viewModel::toggle,
                onSelectDuration = viewModel::selectDuration,
                onPause = { viewModel.start(onStarted = ::close) },
                onCancel = ::close
            )
        }
    }
}

@Composable
fun PauseSheetContent(
    state: PauseSheetState,
    onToggle: (PauseFeature?) -> Unit,
    onSelectDuration: (PauseDuration) -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.ic_pause_ring),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.pause_sheet_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.pause_sheet_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(stringResource(R.string.pause_sheet_what), style = MaterialTheme.typography.titleMedium)
        val options: List<PauseFeature?> = listOf(null) + state.available
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(FEATURE_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { feature ->
                        PauseFeatureCard(
                            label = feature?.let { stringResource(it.titleRes) } ?: stringResource(R.string.pause_everything),
                            icon = pauseFeatureIcon(feature),
                            selected = if (feature == null) state.selection == PauseFeature.ALL
                            else state.selection != PauseFeature.ALL && feature in state.selection,
                            onClick = { onToggle(feature) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(FEATURE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Text(stringResource(R.string.pause_sheet_how_long), style = MaterialTheme.typography.titleMedium)
        val durations = PauseDuration.entries
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            durations.forEachIndexed { index, duration ->
                val selected = state.duration == duration
                val ends = duration.endMillis(state.openedAtMillis, state.morningMinutes)
                SegmentedListItem(
                    onClick = { onSelectDuration(duration) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = durations.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    trailingContent = if (duration == PauseDuration.UNTIL_RESUMED) null else {
                        {
                            Text(
                                text = if (duration == PauseDuration.UNTIL_MORNING) formatClockMillis(context, ends)
                                else stringResource(R.string.pause_sheet_until_time, formatClockMillis(context, ends)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    Text(stringResource(duration.titleRes))
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
            Button(onClick = onPause, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pause_sheet_start))
            }
        }
    }
}

/** One choice in "What to pause", shaped and coloured like the rule editor's pattern cards. */
@Composable
private fun PauseFeatureCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corner by animateDpAsState(
        targetValue = if (selected) 28.dp else 16.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pauseFeatureCorner"
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pauseFeatureContainer"
    )
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        modifier = modifier.semantics { role = Role.Checkbox }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * On Home while a pause is in force: what's dark, until when, and what still lights, with
 * Resume and a way back into the sheet to change it.
 */
@Composable
fun PauseCard(
    pause: PauseState,
    enabledFeatures: List<PauseFeature>,
    onResume: () -> Unit,
    onChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title = if (pause.pausesEverything) stringResource(R.string.pause_card_title_all)
    else stringResource(R.string.pause_card_title_some, pauseFeaturesLabel(context, pause.features))
    val status = if (pause.untilResumed) stringResource(R.string.pause_card_status_until_resumed)
    else stringResource(R.string.pause_card_status_until, formatClockMillis(context, pause.untilMillis))
    val stillLit = enabledFeatures.filterNot { it in pause.features }
    val subtitle = listOfNotNull(
        stringResource(R.string.pause_card_desc_queue).takeIf { PauseFeature.NOTIFICATIONS in pause.features },
        stillLit.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.pause_card_desc_still_lit, pauseFeaturesLabel(context, it.toSet()))
        }
    ).joinToString(" ").ifEmpty { stringResource(R.string.pause_card_desc_default) }

    StandardDiagnosticCard(
        title = title,
        subtitle = subtitle,
        icon = ImageVector.vectorResource(R.drawable.ic_pause_ring),
        statusText = status,
        isOk = true,
        modifier = modifier,
        bottomAction = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onChange, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.pause_card_extend))
                }
                Button(onClick = onResume, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.pause_card_resume))
                }
            }
        }
    )
}

/** "Everything", or the paused features by name in the app's usual order. */
internal fun pauseFeaturesLabel(context: Context, features: Set<PauseFeature>): String =
    if (features == PauseFeature.ALL) context.getString(R.string.pause_everything)
    else PauseFeature.entries.filter { it in features }.joinToString(", ") { context.getString(it.titleRes) }

/** The tile's subtitle: what's paused and until when. */
internal fun pauseTileSubtitle(context: Context, pause: PauseState): String {
    val until = if (pause.untilResumed) context.getString(R.string.pause_until_resumed)
    else context.getString(R.string.pause_until_time, formatClockMillis(context, pause.untilMillis))
    return context.getString(R.string.pause_tile_subtitle, pauseFeaturesLabel(context, pause.features), until)
}

/** The same icons as Home's page toggle, and the paused ring for Everything. */
@Composable
private fun pauseFeatureIcon(feature: PauseFeature?): ImageVector = when (feature) {
    null -> ImageVector.vectorResource(R.drawable.ic_pause_ring)
    PauseFeature.CALLS -> Icons.Rounded.Call
    PauseFeature.NOTIFICATIONS -> Icons.Rounded.Notifications
    PauseFeature.BATTERY -> Icons.Rounded.BatteryChargingFull
    PauseFeature.GEMINI -> Icons.Rounded.AutoAwesome
}

private val PauseDuration.titleRes: Int
    get() = when (this) {
        PauseDuration.ONE_HOUR -> R.string.pause_duration_one_hour
        PauseDuration.TWO_HOURS -> R.string.pause_duration_two_hours
        PauseDuration.UNTIL_MORNING -> R.string.pause_duration_until_morning
        PauseDuration.UNTIL_RESUMED -> R.string.pause_duration_until_resumed
    }

private const val FEATURE_COLUMNS = 3

@Preview(name = "Pause sheet", showBackground = true, widthDp = 390)
@Composable
private fun PauseSheetPreview() {
    HiLightPlusTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            PauseSheetContent(
                state = PauseSheetState(
                    available = PauseFeature.entries,
                    selection = setOf(PauseFeature.NOTIFICATIONS, PauseFeature.BATTERY),
                    duration = PauseDuration.UNTIL_MORNING,
                    openedAtMillis = System.currentTimeMillis(),
                    morningMinutes = 7 * 60
                ),
                onToggle = {},
                onSelectDuration = {},
                onPause = {},
                onCancel = {}
            )
        }
    }
}
