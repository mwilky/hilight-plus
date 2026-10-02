package com.mwilky.hilight.plus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mwilky.hilight.plus.AppStore
import com.mwilky.hilight.plus.PauseDuration
import com.mwilky.hilight.plus.PauseFeature
import com.mwilky.hilight.plus.enabledPauseFeatures
import com.mwilky.hilight.plus.fitPauseSelection
import com.mwilky.hilight.plus.pauseMorningMinutes
import com.mwilky.hilight.plus.togglePauseSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the pause sheet shows: the features on offer, the choice so far, and when "morning" is. */
data class PauseSheetState(
    val available: List<PauseFeature>,
    val selection: Set<PauseFeature>,
    val duration: PauseDuration,
    val openedAtMillis: Long,
    val morningMinutes: Int
)

class PauseViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppStore.get(application)

    private val _state = MutableStateFlow<PauseSheetState?>(null)
    /** Null until the stored choice has loaded. */
    val state: StateFlow<PauseSheetState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val snapshot = store.snapshot()
            val last = store.lastPauseChoice.first()
            // Changing a pause starts from what it covers now; otherwise from the last choice.
            val features = store.activePause.first()?.features ?: last.features
            val available = snapshot.enabledPauseFeatures()
            _state.value = PauseSheetState(
                available = available,
                selection = fitPauseSelection(features, available),
                duration = last.duration,
                openedAtMillis = System.currentTimeMillis(),
                morningMinutes = pauseMorningMinutes(snapshot.quietHoursEnabled, snapshot.quietHoursEndMinutes)
            )
        }
    }

    /** Null toggles Everything. */
    fun toggle(feature: PauseFeature?) = _state.update { s ->
        s?.copy(selection = togglePauseSelection(s.selection, feature, s.available))
    }

    fun selectDuration(duration: PauseDuration) = _state.update { it?.copy(duration = duration) }

    fun start(onStarted: () -> Unit) {
        val s = _state.value ?: return
        viewModelScope.launch {
            store.startPause(s.selection, s.duration, s.duration.endMillis(System.currentTimeMillis(), s.morningMinutes))
            onStarted()
        }
    }
}
