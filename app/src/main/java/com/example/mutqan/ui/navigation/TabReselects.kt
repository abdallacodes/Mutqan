package com.example.mutqan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** The bar says which tab was tapped while it was already the current one; that tab's screen decides what it means. */
object TabReselects {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = flow

    fun reselected(route: String) {
        flow.tryEmit(route)
    }
}

/** Runs [action] each time the tab at [route] is tapped again, which on every tab here means going back to the top. */
@Composable
fun OnTabReselected(route: String, action: suspend () -> Unit) {
    val latest by rememberUpdatedState(action)
    LaunchedEffect(route) {
        TabReselects.events.collect { if (it == route) latest() }
    }
}
