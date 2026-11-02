package com.mwilky.hilight.plus

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** A notification channel an app has posted on: its id, and the name Android shows for it. */
data class SeenChannel(val id: String, val name: String)

private val Context.channelStore: DataStore<Preferences> by preferencesDataStore(name = "hilight_plus_channels")

/**
 * The channels each app has posted a notification on that could light, so an app rule can switch
 * them off by name. Android only lists another app's channels to a companion app, so the listener
 * remembers the ones it sees instead. Kept apart from the settings: it records what arrived rather
 * than anything the user chose, so it neither re-resolves the waiting lights nor goes in a backup.
 */
class SeenChannels private constructor(private val appContext: Context) {

    // What is already stored, so a notification on a known channel costs no write.
    private val known = ConcurrentHashMap<String, String>()

    suspend fun record(packageName: String, channelId: String, name: String) {
        val cacheKey = "$packageName/$channelId"
        if (known[cacheKey] == name) return
        appContext.channelStore.edit { prefs ->
            val key = keyFor(packageName)
            val channels = decodeSeenChannels(prefs[key]).associate { it.id to it.name } + (channelId to name)
            prefs[key] = encodeSeenChannels(channels.map { (id, channelName) -> SeenChannel(id, channelName) })
        }
        known[cacheKey] = name
    }

    /** [packageName]'s channels seen so far, by name. */
    fun channels(packageName: String): Flow<List<SeenChannel>> =
        appContext.channelStore.data
            .map { decodeSeenChannels(it[keyFor(packageName)]) }
            .distinctUntilChanged()

    private fun keyFor(packageName: String) = stringPreferencesKey("channels_$packageName")

    companion object {
        @Volatile
        private var instance: SeenChannels? = null

        fun get(context: Context): SeenChannels {
            val app = if (context is Application) context else context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: SeenChannels(app).also { instance = it }
            }
        }
    }
}

internal fun encodeSeenChannels(channels: List<SeenChannel>): String =
    JSONObject().apply { channels.forEach { put(it.id, it.name) } }.toString()

/** Sorted by name, as the channel list shows them. Anything unreadable reads as none seen yet. */
internal fun decodeSeenChannels(raw: String?): List<SeenChannel> {
    if (raw.isNullOrBlank()) return emptyList()
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
    return json.keys().asSequence()
        .map { id -> SeenChannel(id, json.optString(id).ifBlank { id }) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        .toList()
}
