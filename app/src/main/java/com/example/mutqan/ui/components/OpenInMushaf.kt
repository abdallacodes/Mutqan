package com.example.mutqan.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.mutqan.R
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.navigation.LocalMushafOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Open in the Mushaf" for the ayah [ayahId] (global id): the Mushaf opens on its page with the ayah tinted. Shown
 * wherever an ayah is, so the reader can always go and see it in place; it draws nothing where the Mushaf cannot be
 * opened from (inside the Mushaf itself).
 */
@Composable
fun OpenInMushaf(ayahId: Int, modifier: Modifier = Modifier, soft: Boolean = false) {
    val open = LocalMushafOpener.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    PaperLink(
        text = stringResource(R.string.home_btn_open_mushaf),
        soft = soft,
        modifier = modifier,
        onClick = {
            scope.launch {
                val page = withContext(Dispatchers.IO) { QuranRepository.getInstance(context).ayahIndex.firstPageOf(ayahId) }
                open(page, ayahId)
            }
        }
    )
}
