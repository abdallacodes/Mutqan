package com.example.mutqan.ui.coverage

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.HifzCoverage
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How the user names a range to add or remove. */
enum class RangeMode { SURAH, PAGE, AYAH }

/** A covered range with its ends spelled as surah and ayah numbers. */
@Immutable
data class CoverageRangeItem(
    val range: IntRange,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int
)

/** A juz with some memorised pages, and the level the user gave all of them (null when mixed or not given). */
@Immutable
data class JuzStrength(val juz: Int, val tier: PlanTier?)

@Immutable
data class CoverageUiState(
    val strengths: List<JuzStrength> = emptyList(),
    val ranges: List<CoverageRangeItem> = emptyList(),
    val ayahCount: Int = 0,
    val pageCount: Int = 0,
    /** Juz whose every ayah is covered. */
    val fullJuz: Set<Int> = emptySet(),
    /** Surahs whose every ayah is covered, and surahs that are covered in part. */
    val fullSurahs: Set<Int> = emptySet(),
    val partialSurahs: Set<Int> = emptySet(),
    val isSet: Boolean = false
)

class CoverageViewModel(
    private val study: StudyRepository,
    private val quran: QuranRepository
) : ViewModel() {

    private val index by lazy { quran.ayahIndex }
    private val juzRanges = MutableStateFlow<Map<Int, IntRange>>(emptyMap())
    private val surahRanges = MutableStateFlow<Map<Int, IntRange>>(emptyMap())
    private val juzPages = MutableStateFlow<Map<Int, List<Int>>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            juzPages.value = quran.pagesByJuz()
            juzRanges.value = (1..30).mapNotNull { juz -> quran.ayahIdRangeOfJuz(juz)?.let { juz to it } }.toMap()
            surahRanges.value = (1..114).mapNotNull { s ->
                val first = quran.ayah(s, 1)?.id
                val last = quran.surah(s)?.let { quran.ayah(s, it.versesCount)?.id }
                if (first != null && last != null) s to (first..last) else null
            }.toMap()
        }
    }

    private data class Inputs(
        val juz: Map<Int, IntRange>,
        val surahs: Map<Int, IntRange>,
        val pages: Map<Int, List<Int>>,
        val rated: Map<Int, PlanTier>
    )

    private val inputs = combine(juzRanges, surahRanges, juzPages, study.selfRatedStrength(), ::Inputs)

    val uiState: StateFlow<CoverageUiState> = combine(study.coverage(), inputs) { ranges, input ->
        val (juz, surahs, pages, rated) = input
        val covered = HifzCoverage.coveredPages(ranges) { index.firstPageOf(it) }
        CoverageUiState(
            strengths = pages.entries.sortedBy { it.key }.mapNotNull { (j, list) ->
                val mine = list.filter { covered?.getOrNull(it - 1) == true }
                if (mine.isEmpty()) null
                else JuzStrength(j, mine.map { rated[it] }.distinct().singleOrNull())
            },
            ranges = ranges.map {
                CoverageRangeItem(it, index.surahOf(it.first), index.ayahOf(it.first), index.surahOf(it.last), index.ayahOf(it.last))
            },
            ayahCount = HifzCoverage.ayahCount(ranges),
            pageCount = HifzCoverage.coveredPages(ranges) { index.firstPageOf(it) }?.count { it } ?: 0,
            fullJuz = juz.filterValues { HifzCoverage.containsAll(ranges, it) }.keys,
            fullSurahs = surahs.filterValues { HifzCoverage.containsAll(ranges, it) }.keys,
            partialSurahs = surahs.filterValues { s ->
                !HifzCoverage.containsAll(ranges, s) && ranges.any { it.first <= s.last && it.last >= s.first }
            }.keys,
            isSet = ranges.isNotEmpty()
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoverageUiState())

    private fun current(): List<IntRange> = uiState.value.ranges.map { it.range }

    fun toggleJuz(juz: Int) {
        val range = juzRanges.value[juz] ?: return
        viewModelScope.launch {
            val ranges = current()
            study.setCoverage(
                if (HifzCoverage.containsAll(ranges, range)) HifzCoverage.remove(ranges, range)
                else HifzCoverage.add(ranges, range)
            )
        }
    }

    /** Marks a whole surah as memorised, or takes it away when it already is entirely. */
    fun toggleSurah(surah: Int) {
        val range = surahRanges.value[surah] ?: return
        viewModelScope.launch {
            val ranges = current()
            study.setCoverage(
                if (HifzCoverage.containsAll(ranges, range)) HifzCoverage.remove(ranges, range)
                else HifzCoverage.add(ranges, range)
            )
        }
    }

    /**
     * Says how well the user knows [juz], marking it as memorised when it is not yet. Choosing the level a
     * memorised juz already has clears the level, and the juz stays memorised.
     */
    fun rateJuz(juz: Int, tier: PlanTier) {
        val pages = juzPages.value[juz] ?: return
        val range = juzRanges.value[juz] ?: return
        val memorised = juz in uiState.value.fullJuz
        val clear = memorised && uiState.value.strengths.firstOrNull { it.juz == juz }?.tier == tier
        viewModelScope.launch {
            if (!memorised) study.setCoverage(HifzCoverage.add(current(), range))
            study.setSelfRated(pages.first()..pages.last(), if (clear) null else tier)
        }
    }

    fun setWholeQuran() {
        viewModelScope.launch(Dispatchers.IO) {
            quran.ayahIdRangeOfPages(1, 604)?.let { study.setCoverage(listOf(it)) }
        }
    }

    fun clear() {
        viewModelScope.launch { study.setCoverage(emptyList()) }
    }

    fun removeRange(range: IntRange) {
        viewModelScope.launch { study.setCoverage(HifzCoverage.remove(current(), range)) }
    }

    /**
     * Adds (or, with [remove], takes away) the range typed by the user. [RangeMode.SURAH] and
     * [RangeMode.PAGE] use only [from] and [to]; [RangeMode.AYAH] also uses the ayah numbers.
     * Returns false, changing nothing, when the numbers are not a valid range.
     */
    suspend fun applyRange(
        mode: RangeMode,
        from: String,
        fromAyah: String,
        to: String,
        toAyah: String,
        remove: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val a = from.toIntOrNull()
        val b = to.toIntOrNull()
        val range: IntRange? = when (mode) {
            RangeMode.SURAH -> {
                if (a == null || b == null || a !in 1..114 || b !in a..114) null
                else {
                    val first = quran.ayah(a, 1)?.id
                    val last = quran.surah(b)?.let { quran.ayah(b, it.versesCount)?.id }
                    if (first != null && last != null) first..last else null
                }
            }
            RangeMode.PAGE -> {
                if (a == null || b == null || a !in 1..604 || b !in a..604) null
                else quran.ayahIdRangeOfPages(a, b)
            }
            RangeMode.AYAH -> {
                val first = if (a != null) fromAyah.toIntOrNull()?.let { quran.ayah(a, it)?.id } else null
                val last = if (b != null) toAyah.toIntOrNull()?.let { quran.ayah(b, it)?.id } else null
                if (first != null && last != null && first <= last) first..last else null
            }
        }
        if (range == null) return@withContext false
        val ranges = current()
        study.setCoverage(if (remove) HifzCoverage.remove(ranges, range) else HifzCoverage.add(ranges, range))
        true
    }
}

class CoverageViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CoverageViewModel(StudyRepository.create(context), QuranRepository.getInstance(context)) as T
}
