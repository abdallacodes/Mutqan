package com.example.qmemo.ui.theme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.ThemeKey
import com.example.qmemo.data.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes the persisted [ThemeKey] as a [StateFlow] and accepts writes via [setTheme].
 * Scoped to the Activity's [ViewModelStore], so every composable that calls
 * `viewModel<ThemeViewModel>()` inside the same Activity gets the same instance.
 */
class ThemeViewModel(private val prefs: ThemePreferences) : ViewModel() {

    val themeKey: StateFlow<ThemeKey> = prefs.themeKey
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.Eagerly,
            initialValue   = ThemeKey.AUTO
        )

    fun setTheme(key: ThemeKey) {
        viewModelScope.launch { prefs.setTheme(key) }
    }
}

class ThemeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ThemeViewModel(ThemePreferences(context.applicationContext)) as T
}
