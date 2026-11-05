package com.example.mutqan.ui.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.entity.SimilarityGroupEntity
import com.example.mutqan.data.local.entity.SimilarityMemberEntity
import com.example.mutqan.data.local.entity.VaultFolderEntity
import com.example.mutqan.domain.MutashabihatLibrary
import com.example.mutqan.domain.ReadyGroup
import com.example.mutqan.domain.ReadyPhrase
import com.example.mutqan.domain.SimilarLibrary
import com.example.mutqan.quran.ArabicNormalization
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.UiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The two prepared lists: repeated phrases, and groups of similar ayahs kept in a folder per surah. */
enum class ReadyTab { PHRASES, SIMILAR }

class ReadyListViewModel(
    private val appContext: Context,
    private val dao: StudyDao,
    private val quran: QuranRepository
) : ViewModel() {

    private val all = MutableStateFlow<List<ReadyPhrase>>(emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tab = MutableStateFlow(ReadyTab.PHRASES)
    val tab: StateFlow<ReadyTab> = _tab.asStateFlow()

    private val _expandedId = MutableStateFlow<Int?>(null)
    val expandedId: StateFlow<Int?> = _expandedId.asStateFlow()

    /** The surah folder that is open in the similar-ayahs list; null shows the folders. */
    private val _openSurah = MutableStateFlow<Int?>(null)
    val openSurah: StateFlow<Int?> = _openSurah.asStateFlow()

    private val _expandedGroupId = MutableStateFlow<Int?>(null)
    val expandedGroupId: StateFlow<Int?> = _expandedGroupId.asStateFlow()

    /** Ready groups by surah: a group is in the folder of every surah one of its ayahs belongs to. */
    private val _surahGroups = MutableStateFlow<Map<Int, List<ReadyGroup>>>(emptyMap())
    val surahGroups: StateFlow<Map<Int, List<ReadyGroup>>> = _surahGroups.asStateFlow()

    private val _events = MutableSharedFlow<UiText>()
    val events: SharedFlow<UiText> = _events.asSharedFlow()

    /** Phrases matching the search; the whole list when the search is empty. */
    val phrases: StateFlow<List<ReadyPhrase>> = combine(all, _query) { list, q ->
        val needle = ArabicNormalization.normalizeForSearch(q)
        if (needle.isBlank()) list else list.filter { needle in it.searchText }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything already in the vault, matched by group description. */
    val addedTexts: StateFlow<Set<String>> = dao.observeGroups()
        .map { groups -> groups.map { it.description }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch { all.value = MutashabihatLibrary.phrases(appContext) }
        viewModelScope.launch {
            val index = quran.ayahIndex
            val bySurah = HashMap<Int, MutableList<ReadyGroup>>()
            for (group in SimilarLibrary.load(appContext).groups) {
                group.members.map { index.surahOf(it.ayahId) }.distinct().forEach { bySurah.getOrPut(it) { ArrayList() }.add(group) }
            }
            _surahGroups.value = bySurah.toSortedMap()
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setTab(tab: ReadyTab) {
        _tab.value = tab
    }

    fun toggle(phrase: ReadyPhrase) {
        _expandedId.value = if (_expandedId.value == phrase.id) null else phrase.id
    }

    fun openSurah(surah: Int?) {
        _openSurah.value = surah
        _expandedGroupId.value = null
    }

    fun toggleGroup(group: ReadyGroup) {
        _expandedGroupId.value = if (_expandedGroupId.value == group.id) null else group.id
    }

    /** Creates a vault group holding every ayah of [phrase]. */
    fun addToVault(phrase: ReadyPhrase) {
        if (phrase.text in addedTexts.value) return
        viewModelScope.launch {
            insertGroup(phrase.text, phrase.verses.map { it.ayahId }, folderId = null)
            _events.emit(UiText(R.string.ready_list_added_toast))
        }
    }

    fun addToVault(group: ReadyGroup) {
        if (group.description in addedTexts.value) return
        viewModelScope.launch {
            insertGroup(group.description, group.members.map { it.ayahId }, folderId = null)
            _events.emit(UiText(R.string.ready_list_added_toast))
        }
    }

    /** Adds the groups of a surah folder to a vault folder named after the surah, skipping any already in the vault. */
    fun addSurahFolder(surah: Int) {
        val groups = _surahGroups.value[surah].orEmpty().filter { it.description !in addedTexts.value }
        if (groups.isEmpty()) return
        viewModelScope.launch {
            val name = SurahData.nameOf(surah)
            val folderId = dao.getAllFoldersSync().firstOrNull { it.parentId == null && it.name == name }?.id
                ?: dao.insertFolder(VaultFolderEntity(name = name)).toInt()
            groups.forEach { insertGroup(it.description, it.members.map { m -> m.ayahId }, folderId) }
            _events.emit(UiText.plural(R.plurals.ready_folder_added_toast, groups.size))
        }
    }

    private suspend fun insertGroup(description: String, ayahIds: List<Int>, folderId: Int?) {
        val groupId = dao.insertSimilarityGroup(SimilarityGroupEntity(description = description, folderId = folderId)).toInt()
        dao.insertSimilarityMembers(ayahIds.map { SimilarityMemberEntity(groupId, it) })
    }
}

class ReadyListViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext
        return ReadyListViewModel(app, AppDatabase.getInstance(app).studyDao(), QuranRepository.getInstance(app)) as T
    }
}
