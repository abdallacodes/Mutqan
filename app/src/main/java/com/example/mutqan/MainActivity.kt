package com.example.mutqan

import android.app.KeyguardManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.navigation.AppNavHost
import com.example.mutqan.ui.theme.MutqanTheme
import com.example.mutqan.ui.theme.ThemeViewModel
import com.example.mutqan.ui.theme.ThemeViewModelFactory

class MainActivity : AppCompatActivity() {

    /** True until the user has proved it is them; only matters while the app lock is on. */
    private var locked by mutableStateOf(true)
    private var leftAt = 0L

    private val unlockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) locked = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = UserPreferencesRepository(applicationContext)
        setContent {
            // ThemeViewModel is scoped to the Activity's ViewModelStore.
            // AppNavHost (and SettingsBottomSheet inside it) will share the same instance.
            val themeViewModel: ThemeViewModel =
                viewModel(factory = ThemeViewModelFactory(this))
            val themeKey by themeViewModel.themeKey.collectAsState()
            val lockEnabled: Boolean? by preferences.appLockEnabled.collectAsState(initial = null)

            MutqanTheme(themeKey = themeKey) {
                when {
                    lockEnabled == null -> Spacer(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    lockEnabled == true && locked -> LockScreen(onUnlock = ::askForDeviceCredential)
                    else -> AppNavHost(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Short trips out of the app (a file picker, the unlock screen itself) do not lock it again.
        if (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > LOCK_AFTER_MS) locked = true
    }

    /** Opens the system PIN / pattern / fingerprint screen; with no screen lock set there is nothing to ask. */
    private fun askForDeviceCredential() {
        val intent = getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent(getString(R.string.lock_prompt_title), null)
        if (intent == null) locked = false else unlockLauncher.launch(intent)
    }

    private companion object {
        const val LOCK_AFTER_MS = 30_000L
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OrnamentDivider()
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(24.dp))
        PaperButton(text = stringResource(R.string.lock_btn_unlock), onClick = onUnlock)
    }
}
