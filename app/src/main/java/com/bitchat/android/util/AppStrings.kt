package com.bitchat.android.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * Translated strings for code without a Context (system messages from commands and managers).
 * Initialised by BitchatApplication; before that the English fallback passed by the caller is used.
 */
object AppStrings {
    @Volatile
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String =
        app?.resources?.getString(id, *args) ?: id.toString()

    /** Like [get], with the English text used before init (unit tests, early startup). */
    fun getOr(@StringRes id: Int, fallback: String, vararg args: Any): String =
        app?.resources?.getString(id, *args) ?: String.format(fallback, *args)
}
