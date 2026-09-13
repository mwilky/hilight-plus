package com.mwilky.hilight.plus

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parse a JSON array of rules.
 * A broken array keeps [lastGood] instead of becoming empty.
 * A broken item is skipped; if every item fails and [lastGood] exists, keep it.
 */
internal fun <T> parseRuleArray(
    raw: String?,
    lastGood: List<T>,
    parseItem: (JSONObject) -> T
): List<T> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = try {
        JSONArray(raw)
    } catch (_: Exception) {
        return lastGood
    }
    val list = ArrayList<T>(array.length())
    var anyFailed = false
    for (i in 0 until array.length()) {
        val item = try {
            parseItem(array.getJSONObject(i))
        } catch (_: Exception) {
            anyFailed = true
            null
        }
        if (item != null) list.add(item)
    }
    if (list.isEmpty() && anyFailed && lastGood.isNotEmpty()) return lastGood
    return list
}

internal fun <T> encodeRuleArray(rules: List<T>, toJson: (T) -> JSONObject): String {
    val array = JSONArray()
    rules.forEach { array.put(toJson(it)) }
    return array.toString()
}
