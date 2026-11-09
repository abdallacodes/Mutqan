package com.example.mutqan.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.mutqan.domain.update.UpdateManager

/**
 * What Today shows about a new version, or null when there is nothing to show. The direct (GitHub) build looks for one
 * on its own (at most once an hour) and offers it as a card; the Google Play build has a file of the same name that
 * always returns null, because Play updates that app.
 */
@Composable
fun rememberUpdateOffer(): (@Composable () -> Unit)? {
    val context = LocalContext.current
    val updates = remember(context) { UpdateManager.get(context) }
    val state by updates.state.collectAsState()
    LaunchedEffect(Unit) { updates.checkIfDue() }
    return if (state.isOffered) ({ UpdateCard(state, updates) }) else null
}
