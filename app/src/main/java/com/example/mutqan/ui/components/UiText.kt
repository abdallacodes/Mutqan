package com.example.mutqan.ui.components

import android.content.Context
import androidx.annotation.StringRes

/**
 * A message a ViewModel wants shown, as a string resource plus arguments. The screen resolves it
 * with the current locale, so messages follow the in-app language instead of being fixed text.
 */
class UiText(@StringRes private val res: Int, private vararg val args: Any) {
    fun asString(context: Context): String = context.getString(res, *args)
}
