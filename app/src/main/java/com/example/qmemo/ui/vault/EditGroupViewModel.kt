package com.example.qmemo.ui.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.MemberVerseRef
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.data.local.entity.SimilarityGroupEntity
import com.example.qmemo.data.local.entity.SimilarityMemberEntity
import com.example.qmemo.data.local.entity.VerseEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── Strength model ────────────────────────────────────────────────────────────

enum class MasterStrength(val id: Int, val label: String) {
    WEAK(1, "Weak"),
    STABLE(2, "Stable"),
    SOLID(3, "Solid");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: WEAK
    }
}

// ── Verse-add feedback ────────────────────────────────────────────────────────

sealed interface AddVerseResult {
    data object Idle : AddVerseResult
    data object Added : AddVerseResult
    data object InvalidRef : AddVerseResult          // Surah/Ayah not found in DB
    data object AlreadyMember : AddVerseResult       // verse already in this group
    data object DescriptionRequired : AddVerseResult // must enter description before adding verse
}

// ── UI State ──────────────────────────────────────────────────────────────────

data class EditGroupUiState(
    val description: String = "",
    val memorizationNotes: String = "",
    val strength: MasterStrength = MasterStrength.WEAK,
    val folderId: Int? = null,
    // ── Verse picker fields ──
    val selectedSurahId: Int? = null,   // set when user picks from dropdown
    val surahFilterQuery: String = "",  // live filter text inside the surah dropdown
    val ayahInput: String = "",         // manual ayah entry or filled by search pick
    val textSearchQuery: String = "",   // Arabic text search field
    // ── New search filters ──
    val searchFilterSurahId: Int? = null,
    val searchFilterJuzStart: Int? = null,
    val searchFilterJuzEnd: Int? = null,
    // ── Feedback ──
    val addVerseResult: AddVerseResult = AddVerseResult.Idle,
    val isSaved: Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class EditGroupViewModel(private val dao: QuranDao) : ViewModel() {

    private val _uiState = MutableStateFlow(EditGroupUiState())
    val uiState: StateFlow<EditGroupUiState> = _uiState.asStateFlow()

    // Holds the DB id after the group row is first persisted
    private val _savedGroupId = MutableStateFlow<Int?>(null)

    /**
     * Live member list: emits immediately when [_savedGroupId] is set,
     * and re-emits on every subsequent DB mutation.
     */
    val members: StateFlow<List<MemberVerseRef>> = _savedGroupId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else dao.getMembersForGroup(id)
        }
        .stateIn(
            scope   = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Live Arabic text search results.
     * Uses the improved DAO search with filters.
     */
    val textSearchResults: StateFlow<List<VerseEntity>> = _uiState
        .debounce(300L)
        .flatMapLatest { state ->
            val query = state.textSearchQuery
            if (query.length < 2) return@flatMapLatest flowOf(emptyList())
            
            flow {
                val results = dao.searchVerses(
                    query = query.normalizeArabic(),
                    surahId = state.searchFilterSurahId,
                    juzStart = state.searchFilterJuzStart,
                    juzEnd = state.searchFilterJuzEnd
                )
                emit(results)
            }
        }
        .stateIn(
            scope   = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Set initial folder for a new group. */
    fun setInitialFolder(folderId: Int?) {
        _uiState.update { it.copy(folderId = folderId) }
    }

    /** Load an existing group into the editor (edit mode). */
    fun loadGroup(groupId: Int) {
        viewModelScope.launch {
            val group = dao.getGroupById(groupId) ?: return@launch
            _savedGroupId.value = groupId
            _uiState.update {
                it.copy(
                    description        = group.description,
                    memorizationNotes  = group.memorizationNotes,
                    strength           = MasterStrength.fromId(group.masterStrength),
                    folderId           = group.folderId,
                    isSaved            = true
                )
            }
        }
    }

    // ── Field update handlers ──────────────────────────────────────────────────

    fun onDescriptionChange(value: String) =
        _uiState.update { it.copy(description = value) }

    fun onMemorizationNotesChange(value: String) =
        _uiState.update { it.copy(memorizationNotes = value) }

    fun onStrengthChange(s: MasterStrength) =
        _uiState.update { it.copy(strength = s) }

    fun onSurahSelected(id: Int) = _uiState.update {
        it.copy(selectedSurahId = id, surahFilterQuery = "", addVerseResult = AddVerseResult.Idle)
    }

    fun onSurahFilterChange(v: String) = _uiState.update {
        it.copy(surahFilterQuery = v, selectedSurahId = null, addVerseResult = AddVerseResult.Idle)
    }

    fun onAyahInputChange(v: String) = _uiState.update {
        it.copy(ayahInput = v.filter(Char::isDigit).take(3), addVerseResult = AddVerseResult.Idle)
    }

    fun onTextSearchChange(v: String) = _uiState.update {
        it.copy(textSearchQuery = v, addVerseResult = AddVerseResult.Idle)
    }

    // ── New search filter handlers (Mutually Exclusive) ──
    fun onSearchFilterSurahSelected(id: Int?) = _uiState.update { 
        it.copy(
            searchFilterSurahId = id,
            searchFilterJuzStart = null, // Clear Juz when Surah is selected
            searchFilterJuzEnd = null
        ) 
    }
    
    fun onSearchFilterJuzRangeChange(start: Int?, end: Int?) = _uiState.update {
        it.copy(
            searchFilterJuzStart = start, 
            searchFilterJuzEnd = end,
            searchFilterSurahId = null // Clear Surah when Juz is selected
        )
    }

    /**
     * Called when the user clicks the "Check" (V) icon on a search result.
     * Adds the verse but DOES NOT clear the search query or filters.
     */
    fun onVerseSearchAdd(verse: VerseEntity) {
        val state = _uiState.value
        viewModelScope.launch {
            // Auto-persist the group header on the first verse add
            if (_savedGroupId.value == null) {
                if (state.description.isBlank()) {
                    _uiState.update { it.copy(addVerseResult = AddVerseResult.DescriptionRequired) }
                    return@launch
                }
                val newId = dao.insertSimilarityGroup(
                    SimilarityGroupEntity(
                        description        = state.description.trim(),
                        masterStrength     = state.strength.id,
                        memorizationNotes  = state.memorizationNotes.trim(),
                        folderId           = state.folderId
                    )
                )
                _savedGroupId.value = newId.toInt()
                _uiState.update { it.copy(isSaved = true) }
            }

            val groupId = _savedGroupId.value ?: return@launch
            if (members.value.any { it.verseId == verse.id }) {
                _uiState.update { it.copy(addVerseResult = AddVerseResult.AlreadyMember) }
                return@launch
            }

            dao.insertSimilarityMember(SimilarityMemberEntity(groupId = groupId, verseId = verse.id))
            _uiState.update { it.copy(addVerseResult = AddVerseResult.Added) }
        }
    }

    fun clearAddResult() = _uiState.update { it.copy(addVerseResult = AddVerseResult.Idle) }

    // ── Persistence ───────────────────────────────────────────────────────────

    fun saveGroup() {
        val state = _uiState.value
        if (state.description.isBlank()) return
        viewModelScope.launch {
            val existingId = _savedGroupId.value
            if (existingId == null) {
                val newId = dao.insertSimilarityGroup(
                    SimilarityGroupEntity(
                        description        = state.description.trim(),
                        masterStrength     = state.strength.id,
                        memorizationNotes  = state.memorizationNotes.trim(),
                        folderId           = state.folderId
                    )
                )
                _savedGroupId.value = newId.toInt()
            } else {
                dao.updateSimilarityGroup(
                    SimilarityGroupEntity(
                        id                 = existingId,
                        description        = state.description.trim(),
                        masterStrength     = state.strength.id,
                        memorizationNotes  = state.memorizationNotes.trim(),
                        folderId           = state.folderId
                    )
                )
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun addVerse() {
        val state   = _uiState.value
        val surahId = state.selectedSurahId
        val ayah    = state.ayahInput.toIntOrNull()

        viewModelScope.launch {
            if (_savedGroupId.value == null) {
                if (state.description.isBlank()) {
                    _uiState.update { it.copy(addVerseResult = AddVerseResult.DescriptionRequired) }
                    return@launch
                }
                val newId = dao.insertSimilarityGroup(
                    SimilarityGroupEntity(
                        description        = state.description.trim(),
                        masterStrength     = state.strength.id,
                        memorizationNotes  = state.memorizationNotes.trim(),
                        folderId           = state.folderId
                    )
                )
                _savedGroupId.value = newId.toInt()
                _uiState.update { it.copy(isSaved = true) }
            }

            val groupId = _savedGroupId.value ?: return@launch
            val verse   = if (surahId != null && ayah != null) dao.findVerse(surahId, ayah) else null

            if (verse == null) {
                _uiState.update { it.copy(addVerseResult = AddVerseResult.InvalidRef) }
                return@launch
            }

            if (members.value.any { it.verseId == verse.id }) {
                _uiState.update { it.copy(addVerseResult = AddVerseResult.AlreadyMember) }
                return@launch
            }

            dao.insertSimilarityMember(SimilarityMemberEntity(groupId = groupId, verseId = verse.id))
            _uiState.update {
                it.copy(
                    selectedSurahId  = null,
                    surahFilterQuery = "",
                    ayahInput        = "",
                    textSearchQuery  = "",
                    addVerseResult   = AddVerseResult.Added
                )
            }
        }
    }

    fun removeMember(member: MemberVerseRef) {
        val groupId = _savedGroupId.value ?: return
        viewModelScope.launch {
            dao.deleteSimilarityMember(SimilarityMemberEntity(groupId = groupId, verseId = member.verseId))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private val arabicDiacriticsRegex  = Regex("[\u064B-\u065F]")
private val arabicAlefVariantsRegex = Regex("[\u0622\u0623\u0625\u0671\u0670\u0621\u0626\u0624]") // آ أ إ ٱ ٰ ء ئ ؤ
private val arabicYaaVariantsRegex = Regex("[\u0649]") // ى
private val arabicTehVariantsRegex = Regex("[\u0629]") // ة

/**
 * Normalizes an Arabic string for search comparison:
 * 1. Strips common diacritics.
 * 2. Normalizes ALL Alef/Hamza variants to plain Alef (\u0627).
 * 3. Normalizes ى to ي (\u064A).
 * 4. Normalizes ة to ه (\u0647).
 */
private fun String.normalizeArabic() = replace(arabicDiacriticsRegex, "")
    .replace(arabicAlefVariantsRegex, "\u0627")
    .replace(arabicYaaVariantsRegex, "\u064A")
    .replace(arabicTehVariantsRegex, "\u0647")

class EditGroupViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EditGroupViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
