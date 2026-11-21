package com.example.mutqan.ui.time

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.mutqan.domain.time.TimeKind
import com.example.mutqan.domain.time.TimeRepository
import com.example.mutqan.domain.time.UserActivity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Counts the time this screen is in use toward [kind]: while it is in front and, within the kind's idle limit, touched
 * (or while [keepGoing] says so: the recitation playing in the Mushaf). It is added up every minute and when the screen
 * goes to the background, so little is lost if the app is closed.
 */
@Composable
fun TrackTime(kind: TimeKind, keepGoing: () -> Boolean = { false }) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val keep by rememberUpdatedState(keepGoing)
    LaunchedEffect(kind, lifecycle) {
        val times = TimeRepository(context)
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Opening the screen is using it.
            UserActivity.touched()
            var pending = 0L
            var last = SystemClock.elapsedRealtime()
            fun count(now: Long) {
                // A long gap (the phone asleep with the screen on top) is not counted as use.
                val step = (now - last).coerceIn(0L, TICK_MS * 2)
                last = now
                if (UserActivity.activeWithin(kind.idleMs, now) || keep()) pending += step
            }
            try {
                while (true) {
                    delay(TICK_MS)
                    count(SystemClock.elapsedRealtime())
                    if (pending >= FLUSH_MS) {
                        times.add(kind, pending)
                        pending = 0
                    }
                }
            } finally {
                count(SystemClock.elapsedRealtime())
                if (pending > 0) withContext(NonCancellable) { times.add(kind, pending) }
            }
        }
    }
}

private const val TICK_MS = 5_000L
private const val FLUSH_MS = 60_000L
