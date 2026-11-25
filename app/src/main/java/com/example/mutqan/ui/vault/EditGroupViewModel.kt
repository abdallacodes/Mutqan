package com.example.mutqan.ui.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.entity.SimilarityGroupEntity
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the editor's fields hold: the group's description, its notes, how well it is known, and its folder. */
data class GroupForm(
    val description: String = "",
    val notes: String = "",
    val quality: Float = DEFAULT_QUALITY,
    val folderId: Int? = null
) {
    companion object {
        const val DEFAULT_QUALITY = 0.5f
    }
}

/**
 * The mutashabihat group editor. It works on a copy: the fields and the verses change here only, and [save] writes
 * them together, so leaving without saving leaves the group as it was (the screen asks first, see [changed]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditGroupViewModel(
    private val appContext: Context,
    /** The group being edited; null for a new one. */
    private val groupId: Int?,
    folderId: Int?,
    private val dao: StudyDao,
    private val quran: QuranRepository
) : ViewModel() {

    /** The group as the editor found it: its fields and its verses. */
    private data class Stored(val form: GroupForm, val ayahIds: List<Int>)

    private val stored = MutableStateFlow<Stored?>(null)
    private val _form = MutableStateFlow<GroupForm?>(null)
    /** The verses of the copy, in reading order; null while the group is read. */
    private val ayahIds = MutableStateFlow<List<Int>?>(null)
    /** The group's id once it has one (a new group gets it when first saved). */
    private var savedId: Int? = groupId
    private var saving = false

    /** The fields; null while the group is read. */
    val form: StateFlow<GroupForm?> = _form.asStateFlow()

    /** The copy's verses, each with its own words; null while the group is read. */
    val members: StateFlow<List<GroupMember>?> = ayahIds
        .mapLatest { ids -> ids?.let { list -> GroupVerses.withOwn(quran, list.mapNotNull { GroupVerses.ref(quran.ayahIndex, it) }) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The verses like the copy's that are not in it, the most alike first; null while they are found. */
    val lookAlikes: StateFlow<LookAlikes?> = ayahIds
        .mapLatest { ids -> ids?.let { GroupVerses.lookAlikes(appContext, quran, it) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True when the copy differs from the group as it was found. */
    val changed: StateFlow<Boolean> = combine(stored, _form, ayahIds) { s, f, ids ->
        s != null && f != null && ids != null && (f != s.form || ids != s.ayahIds)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                val group = groupId?.let { dao.getGroupById(it) }
                if (group == null) Stored(GroupForm(folderId = folderId), emptyList())
                else Stored(
                    GroupForm(group.description, group.memorizationNotes, group.masterQuality, group.folderId),
                    dao.getMemberAyahIds(group.id).sorted()
                )
            }
            stored.value = found
            _form.value = found.form
            ayahIds.value = found.ayahIds
        }
    }

    fun onDescriptionChange(value: String) = _form.update { it?.copy(description = value) }

    fun onNotesChange(value: String) = _form.update { it?.copy(notes = value) }

    fun onQualityChange(value: Float) = _form.update { it?.copy(quality = value) }

    /** Puts [ids] in the copy; returns how many were not in it already. */
    fun add(ids: Collection<Int>): Int {
        val now = ayahIds.value ?: return 0
        val index = quran.ayahIndex
        val fresh = ids.distinct().filter { it !in now && it in index }
        if (fresh.isNotEmpty()) ayahIds.value = (now + fresh).sorted()
        return fresh.size
    }

    /** Takes [ayahId] out of the copy. */
    fun remove(ayahId: Int) = ayahIds.update { it?.minus(ayahId) }

    /** Whether the copy can be saved: a group needs a description and two verses at least. */
    fun canSave(form: GroupForm?, count: Int): Boolean = form != null && form.description.isNotBlank() && count >= MIN_VERSES

    /** Writes the copy (the fields, and the verses put in and taken out), then calls [onSaved]. */
    fun save(onSaved: () -> Unit) {
        val f = _form.value ?: return
        val ids = ayahIds.value ?: return
        val before = stored.value ?: return
        if (saving || !canSave(f, ids.size)) return
        saving = true
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                dao.saveSimilarityGroup(
                    SimilarityGroupEntity(
                        id = savedId ?: 0,
                        description = f.description.trim(),
                        masterQuality = f.quality,
                        memorizationNotes = f.notes.trim(),
                        folderId = f.folderId
                    ),
                    before = before.ayahIds,
                    after = ids
                )
            }
            savedId = id
            stored.value = Stored(f, ids)
            saving = false
            onSaved()
        }
    }

    companion object {
        /** Verses a group needs at least. */
        const val MIN_VERSES = 2
    }
}

class EditGroupViewModelFactory(
    private val context: Context,
    private val groupId: Int?,
    private val folderId: Int?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EditGroupViewModel(
            context.applicationContext,
            groupId,
            folderId,
            AppDatabase.getInstance(context).studyDao(),
            QuranRepository.getInstance(context)
        ) as T
}
