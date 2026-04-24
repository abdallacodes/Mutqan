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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    data object InvalidRef : AddVerseResult       // Surah/Ayah not found in DB
    data object AlreadyMember : AddVerseResult    // verse already in this group
    data object GroupNotSaved : AddVerseResult    // save group first
}

// ── UI State ──────────────────────────────────────────────────────────────────

data class EditGroupUiState(
    val description: String = "",
    val strength: MasterStrength = MasterStrength.WEAK,
    val surahInput: String = "",
    val ayahInput: String = "",
    val addVerseResult: AddVerseResult = AddVerseResult.Idle,
    val isSaved: Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
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
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Load an existing group into the editor (edit mode). */
    fun loadGroup(groupId: Int) {
        viewModelScope.launch {
            val group = dao.getGroupById(groupId) ?: return@launch
            _savedGroupId.value = groupId
            _uiState.update {
                it.copy(
                    description = group.description,
                    strength    = MasterStrength.fromId(group.masterStrength),
                    isSaved     = true
                )
            }
        }
    }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }
    fun onStrengthChange(s: MasterStrength)  = _uiState.update { it.copy(strength = s) }
    fun onSurahInputChange(v: String)  = _uiState.update {
        it.copy(surahInput = v.filter(Char::isDigit).take(3), addVerseResult = AddVerseResult.Idle)
    }
    fun onAyahInputChange(v: String)   = _uiState.update {
        it.copy(ayahInput = v.filter(Char::isDigit).take(3), addVerseResult = AddVerseResult.Idle)
    }
    fun clearAddResult() = _uiState.update { it.copy(addVerseResult = AddVerseResult.Idle) }

    /**
     * Persist (or update) the group header row.
     * Must be called before adding any verse members.
     */
    fun saveGroup() {
        val state = _uiState.value
        if (state.description.isBlank()) return
        viewModelScope.launch {
            val existingId = _savedGroupId.value
            if (existingId == null) {
                val newId = dao.insertSimilarityGroup(
                    SimilarityGroupEntity(
                        description    = state.description.trim(),
                        masterStrength = state.strength.id
                    )
                )
                _savedGroupId.value = newId.toInt()
            } else {
                dao.updateSimilarityGroup(
                    SimilarityGroupEntity(
                        id             = existingId,
                        description    = state.description.trim(),
                        masterStrength = state.strength.id
                    )
                )
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    /**
     * Validates the entered Surah:Ayah, looks up the verse in the DB,
     * and links it to the current group.  Provides instant feedback via [AddVerseResult].
     */
    fun addVerse() {
        val state = _uiState.value
        val groupId = _savedGroupId.value
        if (groupId == null) {
            _uiState.update { it.copy(addVerseResult = AddVerseResult.GroupNotSaved) }
            return
        }

        val surah = state.surahInput.toIntOrNull()
        val ayah  = state.ayahInput.toIntOrNull()

        viewModelScope.launch {
            val verse = if (surah != null && ayah != null) dao.findVerse(surah, ayah) else null

            if (verse == null) {
                _uiState.update { it.copy(addVerseResult = AddVerseResult.InvalidRef) }
                return@launch
            }

            val currentMembers = members.value
            if (currentMembers.any { it.verseId == verse.id }) {
                _uiState.update { it.copy(addVerseResult = AddVerseResult.AlreadyMember) }
                return@launch
            }

            dao.insertSimilarityMember(SimilarityMemberEntity(groupId = groupId, verseId = verse.id))
            _uiState.update { it.copy(surahInput = "", ayahInput = "", addVerseResult = AddVerseResult.Added) }
        }
    }

    fun removeMember(member: MemberVerseRef) {
        val groupId = _savedGroupId.value ?: return
        viewModelScope.launch {
            dao.deleteSimilarityMember(SimilarityMemberEntity(groupId = groupId, verseId = member.verseId))
        }
    }
}

class EditGroupViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EditGroupViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
