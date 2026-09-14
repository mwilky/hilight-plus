package com.mwilky.hilight.plus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mwilky.hilight.plus.AppNotificationRule
import com.mwilky.hilight.plus.AppStore
import com.mwilky.hilight.plus.ContactRule
import com.mwilky.hilight.plus.DndMode
import com.mwilky.hilight.plus.FaceDownMode
import com.mwilky.hilight.plus.MessageContactRule
import com.mwilky.hilight.plus.PatternMode
import com.mwilky.hilight.plus.QuietHoursMode
import com.mwilky.hilight.plus.SettingsSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val INITIAL_STATE = SettingsSnapshot(
    isEnabled = true,
    isOnlyWhenFaceDown = false,
    suppressDuringDnd = false,
    quietHoursEnabled = false,
    quietHoursStartMinutes = 22 * 60,
    quietHoursEndMinutes = 7 * 60,
    isCallLightsEnabled = true,
    contactRules = emptyList(),
    isOtherContactsEnabled = true,
    otherContactsColor = 0xFF4285F4,
    otherContactsPattern = PatternMode.PULSE,
    otherContactsFaceDownMode = FaceDownMode.INHERIT,
    otherContactsDndMode = DndMode.INHERIT,
    otherContactsQuietHoursMode = QuietHoursMode.INHERIT,
    otherContactsQuietHoursStartMinutes = null,
    otherContactsQuietHoursEndMinutes = null,
    isUnknownNumbersEnabled = true,
    unknownNumbersColor = 0xFFFBBC05,
    unknownNumbersPattern = PatternMode.PULSE,
    unknownNumbersFaceDownMode = FaceDownMode.INHERIT,
    unknownNumbersDndMode = DndMode.INHERIT,
    unknownNumbersQuietHoursMode = QuietHoursMode.INHERIT,
    unknownNumbersQuietHoursStartMinutes = null,
    unknownNumbersQuietHoursEndMinutes = null,
    isNotificationsEnabled = true,
    notificationDurationSeconds = 30,
    isCycleNotifications = false,
    isDefaultNotifEnabled = true,
    defaultNotifColor = 0xFFFFFFFF,
    defaultNotifPattern = PatternMode.PULSE,
    defaultNotifFaceDownMode = FaceDownMode.INHERIT,
    isDefaultNotifAutoColor = true,
    defaultNotifDndMode = DndMode.INHERIT,
    defaultNotifQuietHoursMode = QuietHoursMode.INHERIT,
    defaultNotifQuietHoursStartMinutes = null,
    defaultNotifQuietHoursEndMinutes = null,
    messageContactRules = emptyList(),
    appRules = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppStore.get(application)

    val uiState: StateFlow<SettingsSnapshot> = store.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), INITIAL_STATE)

    fun setCallLightsEnabled(enabled: Boolean) = launch { store.setCallLightsEnabled(enabled) }
    fun setOtherContactsEnabled(enabled: Boolean) = launch { store.setOtherContactsEnabled(enabled) }
    fun setUnknownNumbersEnabled(enabled: Boolean) = launch { store.setUnknownNumbersEnabled(enabled) }
    fun setNotificationsEnabled(enabled: Boolean) = launch { store.setNotificationsEnabled(enabled) }
    fun setNotificationDurationSeconds(seconds: Int) = launch { store.setNotificationDurationSeconds(seconds) }
    fun setCycleNotifications(enabled: Boolean) = launch { store.setCycleNotifications(enabled) }
    fun setDefaultNotifEnabled(enabled: Boolean) = launch { store.setDefaultNotifEnabled(enabled) }

    fun saveContactRule(rule: ContactRule) = launch { store.saveContactRule(rule) }
    fun deleteContactRule(ruleId: String) = launch { store.deleteContactRule(ruleId) }
    fun saveMessageContactRule(rule: MessageContactRule) = launch { store.saveMessageContactRule(rule) }
    fun deleteMessageContactRule(ruleId: String) = launch { store.deleteMessageContactRule(ruleId) }
    fun saveAppRule(rule: AppNotificationRule) = launch { store.saveAppRule(rule) }
    fun deleteAppRule(packageName: String) = launch { store.deleteAppRule(packageName) }

    fun setOtherContactsStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int
    ) = launch {
        store.setOtherContactsStyle(
            pattern,
            color,
            faceDown,
            dndMode,
            quietHoursMode,
            quietHoursStartMinutes,
            quietHoursEndMinutes
        )
    }

    fun setUnknownNumbersStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int
    ) = launch {
        store.setUnknownNumbersStyle(
            pattern,
            color,
            faceDown,
            dndMode,
            quietHoursMode,
            quietHoursStartMinutes,
            quietHoursEndMinutes
        )
    }

    fun setDefaultNotifStyle(
        pattern: PatternMode,
        color: Long,
        faceDown: FaceDownMode,
        autoColor: Boolean,
        dndMode: DndMode,
        quietHoursMode: QuietHoursMode,
        quietHoursStartMinutes: Int,
        quietHoursEndMinutes: Int
    ) = launch {
        store.setDefaultNotifStyle(
            pattern,
            color,
            faceDown,
            autoColor,
            dndMode,
            quietHoursMode,
            quietHoursStartMinutes,
            quietHoursEndMinutes
        )
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
