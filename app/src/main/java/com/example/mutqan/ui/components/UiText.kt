package com.example.mutqan.ui.components

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * A message a ViewModel wants shown, as a string resource plus arguments. The screen resolves it
 * with the current locale, so messages follow the in-app language instead of being fixed text.
 */
class UiText private constructor(private val res: Int, private val count: Int?, private val args: Array<out Any>) {
    constructor(@StringRes res: Int, vararg args: Any) : this(res, null, args)

    fun asString(context: Context): String =
        if (count == null) context.getString(res, *args) else context.resources.getQuantityString(res, count, *args)

    companion object {
        /** The form of plural [res] that fits [count] (Arabic has six), with [count] as its argument. */
        fun plural(@PluralsRes res: Int, count: Int) = UiText(res, count = count, args = arrayOf(count))
    }
}
