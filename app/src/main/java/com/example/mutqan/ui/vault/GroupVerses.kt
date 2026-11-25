package com.example.mutqan.ui.vault

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.dao.MemberVerseRef
import com.example.mutqan.domain.SimilarLibrary
import com.example.mutqan.domain.game.NextWords
import com.example.mutqan.quran.AyahIndex
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace

// What a mutashabihat group's screen and its editor share: its verses with their own words, the verses that look like
// them, and the rows and bar that show them, so the two read the same.

/** A verse of a group: where it is, and its own words (1-based ranges): those none of the other verses of the group has. */
data class GroupMember(val ref: MemberVerseRef, val own: List<IntRange>)

/** A verse like the group's that is not in it: where it is, and its words shared with the verse of the group it is most like. */
data class LookAlike(val ayahId: Int, val surahId: Int, val ayahNumber: Int, val page: Int, val shared: List<IntRange>)

/** The look-alikes found: the closest [shown], and how many more there are. */
data class LookAlikes(val shown: List<LookAlike>, val more: Int)

object GroupVerses {
    /** Look-alikes shown at most. */
    const val MOST_SHOWN = 40

    /** Where [ayahId] is, or null for an id the Quran does not have. */
    fun ref(index: AyahIndex, ayahId: Int): MemberVerseRef? =
        if (ayahId in index) MemberVerseRef(ayahId, index.surahOf(ayahId), index.ayahOf(ayahId), index.firstPageOf(ayahId)) else null

    /** The verses [refs] with their own words; a single verse has none marked. Reads the Quran: call it off the main thread. */
    fun withOwn(quran: QuranRepository, refs: List<MemberVerseRef>): List<GroupMember> {
        if (refs.size < 2) return refs.map { GroupMember(it, emptyList()) }
        val next = NextWords.get(quran)
        val ids = refs.map { it.verseId }
        return refs.map { ref -> GroupMember(ref, next.ownWords(ref.verseId, ids)) }
    }

    /**
     * The verses like those of a group of [ids] that are not in it: from the ready lists and from the verses sharing
     * many words with them, each with the verse of the group it is most like, the most alike first. Off the main thread.
     */
    suspend fun lookAlikes(context: Context, quran: QuranRepository, ids: List<Int>, most: Int = MOST_SHOWN): LookAlikes {
        if (ids.isEmpty()) return LookAlikes(emptyList(), 0)
        val listed = SimilarLibrary.load(context.applicationContext)
        val next = NextWords.get(quran)
        val index = quran.ayahIndex
        val inGroup = ids.toSet()
        // Each look-alike with the verse of the group it is most like, and how alike they are.
        val closest = HashMap<Int, Pair<Int, Float>>()
        for (member in ids.distinct()) {
            val found = listed.similarTo(member).map { it.ayahId } + next.lookAlikes(member)
            for (other in found) {
                if (other in inGroup || other !in index) continue
                val likeness = next.likeness(member, other)
                val had = closest[other]
                if (had == null || had.second < likeness) closest[other] = member to likeness
            }
        }
        val ranked = closest.entries.sortedWith(compareByDescending<Map.Entry<Int, Pair<Int, Float>>> { it.value.second }.thenBy { it.key })
        val shown = ranked.take(most).map { (other, best) ->
            LookAlike(other, index.surahOf(other), index.ayahOf(other), index.firstPageOf(other), next.sharedWords(best.first, other))
        }
        return LookAlikes(shown, (ranked.size - shown.size).coerceAtLeast(0))
    }
}

/** Where a verse is: its surah and number, and its page. */
@Composable
internal fun groupVerseRef(surahId: Int, ayah: Int, page: Int): String =
    stringResource(R.string.saved_ref, SurahData.nameOf(surahId), ayah) + " · " + stringResource(R.string.page_n, page)

/**
 * A verse of a group: where it is, and its words in the Mushaf's script with [own] in colour; tapping it shows it. An
 * optional [trailing] button sits at its end.
 */
@Composable
internal fun GroupVerseRow(
    ref: MemberVerseRef,
    own: List<IntRange>,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = groupVerseRef(ref.surahId, ref.ayahNumber, ref.pageNumber),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                AyahText(ayahId = ref.verseId, size = 22.sp, marked = own, markColor = MaterialTheme.colorScheme.primary)
            }
            trailing?.invoke()
        }
        Hairline(Modifier.fillMaxWidth())
    }
}

/** A look-alike not in the group: chosen or not (the row is the switch), the words it shares in colour, and an eye to see it. */
@Composable
internal fun LookAlikeRow(lookAlike: LookAlike, on: Boolean, onToggle: () -> Unit, onPeek: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp).size(24.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = groupVerseRef(lookAlike.surahId, lookAlike.ayahNumber, lookAlike.page),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                AyahText(
                    ayahId = lookAlike.ayahId,
                    size = 22.sp,
                    color = if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    marked = lookAlike.shared,
                    markColor = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onPeek) {
                Icon(Icons.Outlined.Visibility, contentDescription = stringResource(R.string.cd_preview_verse), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Hairline(Modifier.fillMaxWidth())
    }
}

/** At the foot while verses are chosen: the button that adds them to the group, and a link that clears the choice. */
@Composable
internal fun AddChosenBar(count: Int, onAdd: () -> Unit, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = PaperSpace.Gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PaperButton(
                text = pluralStringResource(R.plurals.group_similar_add, count, count),
                onClick = onAdd,
                modifier = Modifier.weight(1f)
            )
            PaperLink(text = stringResource(R.string.group_similar_clear), onClick = onClear, soft = true)
        }
    }
}
