package com.example.qmemo.ui.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.GroupWithCount
import com.example.qmemo.data.local.dao.QuranDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(private val dao: QuranDao) : ViewModel() {

    val groups: StateFlow<List<GroupWithCount>> = dao
        .getAllGroupsWithMemberCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun deleteGroup(item: GroupWithCount) {
        viewModelScope.launch { dao.deleteSimilarityGroup(item.group) }
    }
}

class VaultViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VaultViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
