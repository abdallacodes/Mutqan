package com.example.qmemo

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.ui.navigation.AppNavHost
import com.example.qmemo.ui.theme.QMemoTheme
import com.example.qmemo.ui.theme.ThemeViewModel
import com.example.qmemo.ui.theme.ThemeViewModelFactory

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ThemeViewModel is scoped to the Activity's ViewModelStore.
            // AppNavHost (and SettingsBottomSheet inside it) will share the same instance.
            val themeViewModel: ThemeViewModel =
                viewModel(factory = ThemeViewModelFactory(this))
            val themeKey by themeViewModel.themeKey.collectAsState()

            QMemoTheme(themeKey = themeKey) {
                AppNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
