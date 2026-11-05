package com.example.mutqan.ui.topics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.AyahTheme
import com.example.mutqan.domain.AyahThemesLibrary
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a passage is: its surah, the numbers of its first and last verse, and the page it opens on. */
private class Place(val theme: AyahTheme, val surah: Int, val from: Int, val to: Int, val page: Int)

@Composable
private fun rememberThemesShown(): Boolean = rememberTopicLanguage().themesVisible()

@Composable
private fun rangeText(place: Place): String =
    if (place.from == place.to) stringResource(R.string.theme_range_one, place.from)
    else stringResource(R.string.theme_range, place.from, place.to)

/** What the passage a verse belongs to is about, with its verses; nothing when QUL names no passage for it or the language hides it. */
@Composable
fun VerseTheme(ayahId: Int) {
    val context = LocalContext.current
    val shown = rememberThemesShown()
    val place by produceState<Place?>(null, ayahId, shown) {
        value = if (!shown) null else withContext(Dispatchers.IO) {
            val theme = AyahThemesLibrary.load(context).themeOf(ayahId) ?: return@withContext null
            val index = QuranRepository.getInstance(context).ayahIndex
            Place(theme, index.surahOf(theme.first), index.ayahOf(theme.first), index.ayahOf(theme.last), index.firstPageOf(theme.first))
        }
    }
    val found = place ?: return

    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hairline()
        Text(
            text = stringResource(R.string.peek_theme_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = found.theme.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = rangeText(found),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

/** Whether the themes can be listed in the current language, for the screens that offer them as a tab. */
@Composable
fun themesAvailable(): Boolean = rememberThemesShown()

/** Every passage of the Quran with what it is about, searchable; picking one gives the page it opens on. */
@Composable
fun ThemesList(onGo: (page: Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val places by produceState<List<Place>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val index = QuranRepository.getInstance(context).ayahIndex
            AyahThemesLibrary.load(context).themes.map {
                Place(it, index.surahOf(it.first), index.ayahOf(it.first), index.ayahOf(it.last), index.firstPageOf(it.first))
            }
        }
    }
    val found = remember(places, query) {
        val words = query.trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) places else places.filter { p -> words.all { p.theme.text.contains(it, ignoreCase = true) } }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.themes_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
            items(found, key = { it.theme.first }) { place ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onGo(place.page) }
                            .padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = place.theme.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.theme_row_place, SurahData.nameOf(place.surah), rangeText(place), place.page),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Hairline(Modifier.padding(horizontal = PaperSpace.Gutter))
                }
            }
        }
    }
}
