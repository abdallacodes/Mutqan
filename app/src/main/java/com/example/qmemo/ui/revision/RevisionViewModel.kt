package com.example.qmemo.ui.revision

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.data.local.entity.RevisionLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

// ── Difficulty model ──────────────────────────────────────────────────────────

enum class Difficulty(val id: Int, val label: String) {
    SMOOTH(1, "Smooth"),
    STRUGGLED(2, "Struggled"),
    CRITICAL(3, "Critical");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: SMOOTH
    }
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class RevisionUiState(
    val startPage: String = "",
    val endPage: String = "",
    val difficulty: Difficulty = Difficulty.SMOOTH,
    val startPageError: Boolean = false,
    val endPageError: Boolean = false,
    /** UTC-midnight millis for the user-selected revision date. */
    val selectedDateMillis: Long = 0L
)

/** Returns UTC midnight of the current calendar day. */
internal fun startOfTodayUtcMillis(): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class RevisionViewModel(private val dao: QuranDao) : ViewModel() {

    private val _uiState = MutableStateFlow(RevisionUiState(selectedDateMillis = startOfTodayUtcMillis()))
    val uiState: StateFlow<RevisionUiState> = _uiState.asStateFlow()

    /** Latest 10 revision logs, newest first. Re-emits on every DB change. */
    val recentLogs: StateFlow<List<RevisionLogEntity>> = dao
        .getAllRevisionLogs()
        .map { it.take(10) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onStartPageChange(value: String) {
        _uiState.update {
            it.copy(startPage = value.filter(Char::isDigit).take(3), startPageError = false)
        }
    }

    fun onEndPageChange(value: String) {
        _uiState.update {
            it.copy(endPage = value.filter(Char::isDigit).take(3), endPageError = false)
        }
    }

    fun onDifficultyChange(difficulty: Difficulty) {
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    fun onDateChange(millis: Long) {
        _uiState.update { it.copy(selectedDateMillis = millis) }
    }

    fun logRevision() {
        val state = _uiState.value
        val start = state.startPage.toIntOrNull()
        val end   = state.endPage.toIntOrNull()

        val startInvalid = start == null || start !in 1..604
        val endInvalid   = end == null || end !in 1..604 || (start != null && end < start)

        if (startInvalid || endInvalid) {
            _uiState.update { it.copy(startPageError = startInvalid, endPageError = endInvalid) }
            return
        }

        viewModelScope.launch {
            dao.insertRevisionLog(
                RevisionLogEntity(
                    startPage  = start!!,
                    endPage    = end!!,
                    timestamp  = System.currentTimeMillis(),
                    difficulty = state.difficulty.id,
                    dateMillis = state.selectedDateMillis
                )
            )
            _uiState.update { RevisionUiState(selectedDateMillis = startOfTodayUtcMillis()) }
        }
    }
}

// ── Factory ───────────────────────────────────────────────────────────────────

class RevisionViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RevisionViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
