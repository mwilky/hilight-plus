package com.mwilky.hilight.plus

/**
 * Exact display-name match. Blank names never match, and a short name
 * must not match as a substring of a longer one ("Ann" vs "Joanne").
 */
internal fun contactNamesMatch(ruleName: String, incomingName: String): Boolean {
    val rule = ruleName.trim()
    val incoming = incomingName.trim()
    if (rule.isEmpty() || incoming.isEmpty()) return false
    return rule.equals(incoming, ignoreCase = true)
}

internal fun <T> firstEnabledNameMatch(
    incomingName: String,
    rules: List<T>,
    nameOf: (T) -> String,
    enabledOf: (T) -> Boolean
): T? {
    if (incomingName.isBlank()) return null
    return rules.firstOrNull { enabledOf(it) && contactNamesMatch(nameOf(it), incomingName) }
}
