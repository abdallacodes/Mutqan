package com.example.mutqan.ui.lists

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.VerseLists
import com.example.mutqan.quran.AyahIndex
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranWord
import com.example.mutqan.ui.components.MushafText
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.components.PaperLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where verses [start]..[end] are: "البقرة · 153", "البقرة · 153–157", or across two surahs. */
fun passageRef(context: Context, start: Int, end: Int): String {
    val index = QuranRepository.getInstance(context).ayahIndex
    val surah = index.surahOf(start)
    val ayah = index.ayahOf(start)
    if (start == end) return context.getString(R.string.saved_ref, SurahData.nameOf(surah), ayah)
    val lastSurah = index.surahOf(end)
    val lastAyah = index.ayahOf(end)
    return if (surah == lastSurah) context.getString(R.string.list_ref_run, SurahData.nameOf(surah), ayah, lastAyah)
    else context.getString(R.string.saved_ref_range, SurahData.nameOf(surah), ayah, SurahData.nameOf(lastSurah), lastAyah)
}

@Composable
fun passageRef(start: Int, end: Int): String {
    val context = LocalContext.current
    return remember(context, start, end) { passageRef(context, start, end) }
}

/** The first verse (global id) of the surah that verse [ayahId] is in. */
fun firstOfSurah(index: AyahIndex, ayahId: Int): Int = ayahId - index.ayahOf(ayahId) + 1

/** The last verse (global id) of the surah that verse [ayahId] is in. */
fun lastOfSurah(index: AyahIndex, ayahId: Int): Int {
    val surah = index.surahOf(ayahId)
    var id = ayahId
    while ((id + 1) in index && index.surahOf(id + 1) == surah) id++
    return id
}

/** Verses [start]..[end] as the Mushaf prints them, with their end markers; cut after [most] words, then "…". */
@Composable
fun PassageText(start: Int, end: Int, most: Int, modifier: Modifier = Modifier.fillMaxWidth(), size: TextUnit = 22.sp) {
    val context = LocalContext.current
    val span by produceState<MushafSpan?>(null, start, end, most) {
        value = withContext(Dispatchers.IO) {
            val quran = QuranRepository.getInstance(context)
            val index = quran.ayahIndex
            val words = ArrayList<QuranWord>()
            var id = start
            while (id <= end && words.size <= most) {
                words += quran.wordsOfAyah(index.surahOf(id), index.ayahOf(id))
                id++
            }
            val cut = words.size > most || id <= end
            MushafSpan.of(words.take(most)).let { if (cut) it + MushafSpan.More else it }
        }
    }
    span?.let { MushafText(span = it, modifier = modifier, size = size, color = MaterialTheme.colorScheme.onSurface) }
}

/**
 * Names a new list or renames one, with an optional note. A name the user already gave another list (the same but
 * for diacritics or case, see [VerseLists.nameKey]) is not taken: [takenKeys] are the other lists' keys.
 */
@Composable
fun ListNameDialog(
    title: String,
    initialName: String,
    initialNote: String,
    takenKeys: Set<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var note by remember { mutableStateOf(initialNote) }
    val clean = VerseLists.cleanName(name)
    val taken = clean.isNotEmpty() && VerseLists.nameKey(clean) in takenKeys
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    PaperDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(VerseLists.NAME_MAX) },
                    label = { Text(stringResource(R.string.list_name_label)) },
                    singleLine = true,
                    isError = taken,
                    supportingText = if (taken) {
                        { Text(stringResource(R.string.list_name_taken)) }
                    } else null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.list_note_label)) },
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            PaperLink(text = stringResource(R.string.btn_save), onClick = { if (clean.isNotEmpty() && !taken) onSave(clean, note.trim()) })
        },
        dismissButton = { PaperLink(text = stringResource(R.string.btn_cancel), onClick = onDismiss, soft = true) }
    )
}
