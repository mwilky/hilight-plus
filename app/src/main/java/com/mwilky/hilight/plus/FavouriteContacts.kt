package com.mwilky.hilight.plus

import android.content.Context
import android.provider.ContactsContract

/**
 * True when a starred (favourite) contact has this display name. Calls and notifications only
 * carry the sender's display name, so favourites are matched by name the same way custom rules
 * are; two contacts sharing a name count as a favourite if either is starred.
 */
internal fun isFavouriteContactName(context: Context, displayName: String): Boolean {
    val name = displayName.trim()
    if (name.isEmpty()) return false
    return runCatching {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ? COLLATE NOCASE AND ${ContactsContract.Contacts.STARRED} = 1",
            arrayOf(name),
            null
        )?.use { it.moveToFirst() }
    }.getOrNull() == true
}
