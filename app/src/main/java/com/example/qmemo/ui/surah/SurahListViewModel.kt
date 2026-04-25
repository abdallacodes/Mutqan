package com.example.qmemo.ui.surah

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.SurahInfo
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.QuranDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SurahListItem(
    val info: SurahInfo,
    val verseCount: Int,
    val startJuz: Int,
    /** Mushaf page (1–604) where this Surah begins; 0 if not loaded yet. */
    val startPage: Int,
    val groupCount: Int
)

class SurahListViewModel(private val dao: QuranDao) : ViewModel() {

    /**
     * Merges the hardcoded Surah names with live DB metadata.
     * The DB query is reactive — the list updates automatically whenever
     * a new SimilarityGroup is linked to a Surah.
     *
     * While the verses table is still being pre-populated (first launch),
     * the initial value is the full list at zero counts; it transitions to
     * real data as soon as the DB emits.
     */
    val surahs: StateFlow<List<SurahListItem>> = dao
        .getSurahMetaList()
        .map { metaList ->
            val metaById = metaList.associateBy { it.surahId }
            SurahData.ALL.map { info ->
                val meta = metaById[info.id]
                SurahListItem(
                    info        = info,
                    verseCount  = meta?.verseCount ?: 0,
                    startJuz    = meta?.startJuz    ?: 0,
                    startPage   = meta?.startPage   ?: 0,
                    groupCount  = meta?.groupCount  ?: 0
                )
            }
        }
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = SurahData.ALL.map { SurahListItem(it, 0, 0, 0, 0) }
        )
}

class SurahListViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SurahListViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
