package com.mwilky.hilight.plus

import org.json.JSONArray
import org.json.JSONObject

/**
 * The settings as a file the user keeps, and back: to carry them across a reinstall, say from the
 * Play Store build to a test build. Each value keeps its type, so a restore writes exactly what
 * was there.
 */
internal object SettingsBackup {

    const val FORMAT = "hilight-plus-settings"
    private const val VERSION = 1

    /**
     * Left out both ways. The purchase is the Play Store's to say, and a file must not unlock the
     * app; the trial's real start lives with the phone. A pause in force belongs to the moment.
     */
    val EXCLUDED = setOf("purchased", "trial_start_ms", "pause_until_ms", "pause_features")

    fun encode(values: Map<String, Any>, appVersionCode: Int): String {
        val settings = JSONObject()
        values.filterKeys { it !in EXCLUDED }.toSortedMap().forEach { (key, value) ->
            val (type, json) = when (value) {
                is Boolean -> "boolean" to value
                is Int -> "int" to value
                is Long -> "long" to value
                is Float -> "float" to value.toDouble()
                is Double -> "double" to value
                is String -> "string" to value
                is Set<*> -> "string_set" to JSONArray(value.filterIsInstance<String>())
                else -> return@forEach
            }
            settings.put(key, JSONObject().put("type", type).put("value", json))
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("appVersion", appVersionCode)
            .put("settings", settings)
            .toString(2)
    }

    /** The values in [raw]. Fails as a whole on anything it doesn't recognise, so nothing half-restores. */
    fun decode(raw: String): Result<Map<String, Any>> = runCatching {
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) { "Not a HiLight Plus settings backup" }
        require(root.optInt("version") in 1..VERSION) { "Backup version ${root.optInt("version")} isn't supported" }
        val settings = root.getJSONObject("settings")
        buildMap {
            settings.keys().forEach { key ->
                if (key in EXCLUDED) return@forEach
                val entry = settings.getJSONObject(key)
                put(key, when (val type = entry.getString("type")) {
                    "boolean" -> entry.getBoolean("value")
                    "int" -> entry.getInt("value")
                    "long" -> entry.getLong("value")
                    "float" -> entry.getDouble("value").toFloat()
                    "double" -> entry.getDouble("value")
                    "string" -> entry.getString("value")
                    "string_set" -> entry.getJSONArray("value").let { array ->
                        (0 until array.length()).map(array::getString).toSet()
                    }
                    else -> error("Unknown type $type for $key")
                })
            }
        }
    }
}
