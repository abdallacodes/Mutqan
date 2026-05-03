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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ── History models ────────────────────────────────────────────────────────────

/** All revision logs that share the same month and year, e.g. "APRIL 2026". */
data class RevisionLogGroup(
    val label: String,
    val logs: List<RevisionLogEntity>
)

/**
 * Mutable form state for the in-place edit dialog.
 * Carries the original log entity so [RevisionHistoryViewModel.saveEdit] can
 * create a precise copy with only the edited fields changed.
 */
data class EditLogUiState(
    val originalLog: RevisionLogEntity,
    val startPage: String,
    val endPage: String,
    val manualQuality: Float,
    val selectedDateMillis: Long,
    val startError: Boolean = false,
    val endError: Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class RevisionHistoryViewModel(private val dao: QuranDao) : ViewModel() {

    private val monthYearFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).also {
        it.timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * ALL revision logs grouped by month-year (newest group first, newest log
     * first within each group).  Uses [RevisionLogEntity.dateMillis] when set;
     * falls back to [RevisionLogEntity.timestamp] for legacy entries.
     */
    val groupedLogs: StateFlow<List<RevisionLogGroup>> = dao
        .getAllRevisionLogs()
        .map { logs ->
            logs.groupBy { log ->
                val millis = if (log.dateMillis > 0L) log.dateMillis else log.timestamp
                monthYearFmt.format(Date(millis)).uppercase(Locale.getDefault())
            }
            .entries
            .sortedByDescending { (_, logsInGroup) ->
                logsInGroup.maxOf { log ->
                    if (log.dateMillis > 0L) log.dateMillis else log.timestamp
                }
            }
            .map { (label, logsInGroup) ->
                RevisionLogGroup(
                    label = label,
                    logs  = logsInGroup.sortedByDescending { log ->
                        if (log.dateMillis > 0L) log.dateMillis else log.timestamp
                    }
                )
            }
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── Edit dialog state ─────────────────────────────────────────────────────

    private val _editState = MutableStateFlow<EditLogUiState?>(null)
    val editState: StateFlow<EditLogUiState?> = _editState.asStateFlow()

    fun startEdit(log: RevisionLogEntity) {
        _editState.value = EditLogUiState(
            originalLog        = log,
            startPage          = log.startPage.toString(),
            endPage            = log.endPage.toString(),
            manualQuality      = log.manualStability,
            selectedDateMillis = if (log.dateMillis > 0L) log.dateMillis else startOfTodayUtcMillis()
        )
    }

    fun onEditStartPage(v: String) = _editState.update {
        it?.copy(startPage = v.filter(Char::isDigit).take(3), startError = false)
    }
    fun onEditEndPage(v: String)    = _editState.update {
        it?.copy(endPage = v.filter(Char::isDigit).take(3), endError = false)
    }
    fun onEditQuality(q: Float) = _editState.update { it?.copy(manualQuality = q) }
    fun onEditDate(millis: Long)     = _editState.update { it?.copy(selectedDateMillis = millis) }

    fun saveEdit() {
        val state = _editState.value ?: return
        val start = state.startPage.toIntOrNull()
        val end   = state.endPage.toIntOrNull()

        val startInvalid = start == null || start !in 1..604
        val endInvalid   = end == null || end !in 1..604 || (start != null && end < start)

        if (startInvalid || endInvalid) {
            _editState.update { it?.copy(startError = startInvalid, endError = endInvalid) }
            return
        }

        viewModelScope.launch {
            dao.updateRevisionLog(
                state.originalLog.copy(
                    startPage  = start!!,
                    endPage    = end!!,
                    dateMillis = state.selectedDateMillis,
                    manualStability = state.manualQuality
                    // timestamp (creation time) is preserved unchanged
                )
            )
            _editState.value = null
        }
    }

    fun cancelEdit() { _editState.value = null }

    // ── Delete confirmation state ─────────────────────────────────────────────

    private val _pendingDelete = MutableStateFlow<RevisionLogEntity?>(null)
    val pendingDelete: StateFlow<RevisionLogEntity?> = _pendingDelete.asStateFlow()

    fun requestDelete(log: RevisionLogEntity) { _pendingDelete.value = log }

    fun confirmDelete() {
        val log = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { dao.deleteRevisionLogById(log.id) }
    }

    fun cancelDelete() { _pendingDelete.value = null }
}

// ── Factory ───────────────────────────────────────────────────────────────────

class RevisionHistoryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RevisionHistoryViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
