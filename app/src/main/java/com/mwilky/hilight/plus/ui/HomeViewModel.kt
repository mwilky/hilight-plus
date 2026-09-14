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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppStore.get(application)

    val isCallLightsEnabled = store.isCallLightsEnabled.hot(true)
    val isOtherContactsEnabled = store.isOtherContactsEnabled.hot(true)
    val otherContactsColor = store.otherContactsColor.hot(0xFF4285F4)
    val otherContactsPattern = store.otherContactsPattern.hot(PatternMode.PULSE)
    val otherContactsFaceDownMode = store.otherContactsFaceDownMode.hot(FaceDownMode.INHERIT)
    val otherContactsDndMode = store.otherContactsDndMode.hot(DndMode.INHERIT)
    val otherContactsQuietHoursMode = store.otherContactsQuietHoursMode.hot(QuietHoursMode.INHERIT)
    val otherContactsQuietHoursStartMinutes = store.otherContactsQuietHoursStartMinutes.hot(null)
    val otherContactsQuietHoursEndMinutes = store.otherContactsQuietHoursEndMinutes.hot(null)
    val quietHoursStartMinutes = store.quietHoursStartMinutes.hot(22 * 60)
    val quietHoursEndMinutes = store.quietHoursEndMinutes.hot(7 * 60)
    val isUnknownNumbersEnabled = store.isUnknownNumbersEnabled.hot(true)
    val unknownNumbersColor = store.unknownNumbersColor.hot(0xFFFBBC05)
    val unknownNumbersPattern = store.unknownNumbersPattern.hot(PatternMode.PULSE)
    val unknownNumbersFaceDownMode = store.unknownNumbersFaceDownMode.hot(FaceDownMode.INHERIT)
    val unknownNumbersDndMode = store.unknownNumbersDndMode.hot(DndMode.INHERIT)
    val unknownNumbersQuietHoursMode = store.unknownNumbersQuietHoursMode.hot(QuietHoursMode.INHERIT)
    val unknownNumbersQuietHoursStartMinutes = store.unknownNumbersQuietHoursStartMinutes.hot(null)
    val unknownNumbersQuietHoursEndMinutes = store.unknownNumbersQuietHoursEndMinutes.hot(null)
    val callContactRules = store.contactRules.hot(emptyList())

    val isNotifsEnabled = store.isNotificationsEnabled.hot(true)
    val notifDurationSec = store.notificationDurationSeconds.hot(30)
    val isCycleNotifications = store.isCycleNotifications.hot(false)
    val isDefaultNotifEnabled = store.isDefaultNotifEnabled.hot(true)
    val defaultNotifColor = store.defaultNotifColor.hot(0xFFFFFFFF)
    val defaultNotifPattern = store.defaultNotifPattern.hot(PatternMode.PULSE)
    val defaultNotifFaceDownMode = store.defaultNotifFaceDownMode.hot(FaceDownMode.INHERIT)
    val isDefaultNotifAutoColor = store.isDefaultNotifAutoColor.hot(true)
    val defaultNotifDndMode = store.defaultNotifDndMode.hot(DndMode.INHERIT)
    val defaultNotifQuietHoursMode = store.defaultNotifQuietHoursMode.hot(QuietHoursMode.INHERIT)
    val defaultNotifQuietHoursStartMinutes = store.defaultNotifQuietHoursStartMinutes.hot(null)
    val defaultNotifQuietHoursEndMinutes = store.defaultNotifQuietHoursEndMinutes.hot(null)
    val messageContactRules = store.messageContactRules.hot(emptyList())
    val appRules = store.appRules.hot(emptyList())

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

    private fun <T> Flow<T>.hot(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
