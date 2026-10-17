package com.oloop.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit

/** Device-local settings: the chosen data folder and the user name written to createdBy/updatedBy. */
class Settings(private val context: Context) {
    private val prefs = context.getSharedPreferences("oloop", Context.MODE_PRIVATE)

    var actor: String
        get() = prefs.getString("actor", "") ?: ""
        set(value) = prefs.edit { putString("actor", value.trim()) }

    /** Recently used task types, most recent first, as comma-joined enum names. */
    var recentTypes: List<String>
        get() = (prefs.getString("recent_types", "") ?: "").split(',').filter { it.isNotBlank() }
        set(value) = prefs.edit { putString("recent_types", value.joinToString(",")) }

    val rootUri: Uri?
        get() = prefs.getString("root_uri", null)?.let(Uri::parse)

    fun setRoot(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        prefs.edit { putString("root_uri", uri.toString()) }
    }
}
