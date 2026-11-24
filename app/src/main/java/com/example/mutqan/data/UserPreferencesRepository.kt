package com.example.mutqan.data

import com.example.mutqan.domain.game.GameSeen
import com.example.mutqan.domain.game.GameSlips
import com.example.mutqan.domain.game.LanternsLevels
import com.example.mutqan.domain.game.PlayedVerse
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.mutqan.domain.QuizRetries
import com.example.mutqan.domain.QuizRetry
import com.example.mutqan.domain.PracticeScope
import com.example.mutqan.domain.AdviceMemory
import com.example.mutqan.domain.sync.SyncMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The daily reminder: on or off, and its time in minutes after midnight. */
data class ReminderSettings(val on: Boolean, val minutes: Int) {
    companion object {
        /** 8 in the evening: late enough to catch a day not finished yet, and silent when it is. */
        const val DEFAULT_MINUTES = 20 * 60
    }
}

/** How many shown milestones are remembered (runs of days add new keys over the years). */
private const val MILESTONES_KEPT = 500

/** A quiz is offered after every quarter of an hour of reading, until the reader changes it. */
private const val READING_QUIZ_DEFAULT = 15

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_IS_FIRST_RUN      = booleanPreferencesKey("is_first_run")
        private val KEY_HEATMAP_COACHMARK = booleanPreferencesKey("heatmap_coachmark_seen")
        private val KEY_APP_LOCK          = booleanPreferencesKey("app_lock_enabled")
        private val KEY_LAST_MUSHAF_PAGE  = intPreferencesKey("last_mushaf_page")
        private val KEY_TAFSIR_SOURCE     = intPreferencesKey("tafsir_source")
        private val KEY_MUSHAF_THEME      = stringPreferencesKey("mushaf_theme")
        private val KEY_MUSHAF_ROTATE     = booleanPreferencesKey("mushaf_rotate")
        private val KEY_MUSHAF_SUBJECTS   = booleanPreferencesKey("mushaf_subject_tint")
        private val KEY_MUSHAF_REVEAL     = booleanPreferencesKey("mushaf_reveal_steps")
        private val KEY_MUSHAF_BOOK       = booleanPreferencesKey("mushaf_book_view")
        private val KEY_MUSHAF_LAMP       = booleanPreferencesKey("mushaf_reading_lamp")
        private val KEY_MUSHAF_TWO_PAGE   = booleanPreferencesKey("mushaf_two_page")
        private val KEY_MUSHAF_FILL_WIDTH = booleanPreferencesKey("mushaf_fill_width")
        private val KEY_MUSHAF_GROUP_TINT = booleanPreferencesKey("mushaf_group_ayahs")
        private val KEY_MUSHAF_BARS       = booleanPreferencesKey("mushaf_bars_shown")
        private val KEY_MUSHAF_COVER      = booleanPreferencesKey("mushaf_cover")
        private val KEY_MUSHAF_COVER_BARS = booleanPreferencesKey("mushaf_cover_bars_hidden")
        private val KEY_PLAN_AMOUNT_SINCE = longPreferencesKey("plan_amount_since")
        private val KEY_PLAN_ANSWERED     = longPreferencesKey("plan_advice_answered")
        private val KEY_PLAN_TRIED_FROM   = intPreferencesKey("plan_tried_from")
        private val KEY_PLAN_TRIED_ON     = longPreferencesKey("plan_tried_on")
        private val KEY_RECITER           = stringPreferencesKey("recitation_reciter")
        private val KEY_SEARCH_RECENT     = stringPreferencesKey("search_recent")
        private val KEY_QUIZ_RETRIES      = stringPreferencesKey("quiz_retries")
        private val KEY_READING_QUIZ      = intPreferencesKey("reading_quiz_minutes")
        private val KEY_LANTERNS_BEST     = intPreferencesKey("lanterns_best")
        private val KEY_LANTERNS_ROUNDS   = intPreferencesKey("lanterns_rounds")
        private val KEY_LANTERNS_HIFZ     = booleanPreferencesKey("lanterns_from_hifz")
        private val KEY_LANTERNS_STARS    = stringPreferencesKey("lanterns_stars")
        private val KEY_LANTERNS_SLIPS    = stringPreferencesKey("lanterns_slips")
        private val KEY_LANTERNS_SEEN     = stringPreferencesKey("lanterns_seen")
        private val KEY_LANTERNS_MASTERY  = intPreferencesKey("lanterns_mastery_best")
        private val KEY_LANTERNS_SOUND    = booleanPreferencesKey("lanterns_sound")
        private val KEY_LANTERNS_ALIKE_FOCUS = booleanPreferencesKey("lanterns_alike_focus")
        private val KEY_REMINDER_ON       = booleanPreferencesKey("reminder_on")
        private val KEY_REMINDER_MINUTES  = intPreferencesKey("reminder_minutes")
        private val KEY_REMINDER_OFFERED  = booleanPreferencesKey("reminder_offer_closed")
        private val KEY_WARD_CELEBRATED   = longPreferencesKey("ward_celebrated_day")
        private val KEY_MILESTONES_SEEN   = stringPreferencesKey("milestones_seen")
        private val KEY_MILESTONES_START  = booleanPreferencesKey("milestones_baseline")
        private val KEY_RECITE_REPEAT     = intPreferencesKey("recitation_repeat")
        private val KEY_RECITE_LOOP       = booleanPreferencesKey("recitation_loop")
        private val KEY_RECITE_SURAH      = booleanPreferencesKey("recitation_to_surah_end")
        private val KEY_RECITE_SPEED      = intPreferencesKey("recitation_speed_percent")
        private val KEY_RECITE_RUN        = intPreferencesKey("recitation_run_repeat")
        private val KEY_RECITE_AFTER      = intPreferencesKey("recitation_say_after_percent")
        private val KEY_RECITE_LAST       = intPreferencesKey("recitation_last_ayah")
        private val KEY_PRACTICE_TIERS    = intPreferencesKey("practice_tier_mask")
        private val KEY_PRACTICE_LAST     = intPreferencesKey("practice_last_pages")
        private val KEY_PRACTICE_UNIT     = intPreferencesKey("practice_last_unit")
        private val KEY_SYNC_UID          = stringPreferencesKey("sync_uid")
        private val KEY_SYNC_HASH         = stringPreferencesKey("sync_local_hash")
        private val KEY_SYNC_VERSION      = longPreferencesKey("sync_remote_version")
        private val KEY_SYNC_TIME         = longPreferencesKey("sync_last_time")
        private val KEY_DEVICE_ID         = stringPreferencesKey("sync_device_id")
        private val KEY_DISPLACED         = booleanPreferencesKey("sync_session_displaced")

        private val SYNCED_INTS = listOf(
            KEY_RECITE_REPEAT, KEY_RECITE_SPEED, KEY_RECITE_RUN, KEY_RECITE_AFTER,
            KEY_PRACTICE_TIERS, KEY_PRACTICE_LAST, KEY_PRACTICE_UNIT, KEY_TAFSIR_SOURCE
        )
        private val SYNCED_BOOLEANS = listOf(KEY_RECITE_LOOP, KEY_RECITE_SURAH)
        private val SYNCED_STRINGS = listOf(KEY_RECITER, KEY_MUSHAF_THEME)

        /** How many searches are remembered. */
        const val RECENT_SEARCHES = 8
    }

    /** `true` on fresh installs; `false` after onboarding is completed. */
    val isFirstRun: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_FIRST_RUN] ?: true
    }

    /** `false` until the user dismisses the heatmap coachmark for the first time. */
    val hasSeenHeatmapCoachmark: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HEATMAP_COACHMARK] ?: false
    }

    /** Whether the app asks for the device PIN, pattern or fingerprint when it is opened. */
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_APP_LOCK] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_APP_LOCK] = enabled }
    }

    /** The Mushaf page the user was last reading, or null before they have read any. */
    val lastMushafPage: Flow<Int?> = context.dataStore.data.map { prefs -> prefs[KEY_LAST_MUSHAF_PAGE] }

    suspend fun setLastMushafPage(page: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_LAST_MUSHAF_PAGE] = page }
    }

    /** The name of the Mushaf reading theme (see MushafTheme); empty until the reader chooses. */
    val mushafTheme: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_MUSHAF_THEME].orEmpty() }

    suspend fun setMushafTheme(name: String) {
        context.dataStore.edit { prefs -> prefs[KEY_MUSHAF_THEME] = name }
    }

    /** Whether the Mushaf turns with the device instead of staying portrait (one page) or landscape (two). */
    val mushafRotate: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_MUSHAF_ROTATE] ?: false }

    suspend fun setMushafRotate(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_MUSHAF_ROTATE] = on }
    }

    /** Whether the Mushaf tints the verses of the subjects the user gave the parts of each juz. On unless turned off. */
    val mushafSubjectTint: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_MUSHAF_SUBJECTS] ?: true }

    suspend fun setMushafSubjectTint(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_MUSHAF_SUBJECTS] = on }
    }

    /**
     * How a covered verse is shown when tapped: in steps (first word, then the last word too, then the whole verse) when
     * on, the whole verse at once when off. Off unless turned on.
     */
    val mushafRevealSteps: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_MUSHAF_REVEAL] ?: false }

    suspend fun setMushafRevealSteps(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_MUSHAF_REVEAL] = on }
    }

    /**
     * Whether the one-page Mushaf is a book: each page a page of an open book on a desk (the page in its printed shape,
     * the edges of the other pages and the cover on its outer side, the middle of the book along its bound edge, so the
     * side of the book is always known), its leaves turning like paper. One switch for both (until 2026-11-20 the paper
     * turn had its own, `mushaf_paper_turn`, never released). It takes some width: off unless turned on.
     */
    val mushafBookView: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_MUSHAF_BOOK] ?: false }

    suspend fun setMushafBookView(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_MUSHAF_BOOK] = on }
    }

    /**
     * Whether the Mushaf is shown as if read in a dark room by a warm reading lamp (see ReadingLamp), in any page colour
     * and any view. Off unless turned on.
     */
    val mushafReadingLamp: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_MUSHAF_LAMP] ?: false }

    suspend fun setMushafReadingLamp(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_MUSHAF_LAMP] = on }
    }

    /** What the app remembers of its suggestions to change the plan's pages a day (see PlanAdvisor). */
    val planAdviceMemory: Flow<AdviceMemory> = context.dataStore.data.map { prefs ->
        AdviceMemory(
            amountSince = prefs[KEY_PLAN_AMOUNT_SINCE] ?: 0L,
            lastAnswered = prefs[KEY_PLAN_ANSWERED] ?: 0L,
            triedFrom = prefs[KEY_PLAN_TRIED_FROM] ?: 0,
            triedOn = prefs[KEY_PLAN_TRIED_ON] ?: 0L
        )
    }

    /** The plan's pages a day were set on [day] (by the user, or a suggestion taken; [triedFrom] the amount before a higher one tried, else 0). */
    suspend fun setPlanAmountSet(day: Long, triedFrom: Int = 0) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAN_AMOUNT_SINCE] = day
            prefs[KEY_PLAN_TRIED_FROM] = triedFrom
            prefs[KEY_PLAN_TRIED_ON] = if (triedFrom > 0) day else 0L
        }
    }

    /** A suggestion was answered on [day]; [keepTrying] false also stops offering to go back from a higher amount tried. */
    suspend fun setPlanAdviceAnswered(day: Long, keepTrying: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAN_ANSWERED] = day
            if (!keepTrying) prefs[KEY_PLAN_TRIED_FROM] = 0
        }
    }

    /** How the reader left the Mushaf view (see [MushafViewerState]), so it opens again the same way. */
    val mushafViewerState: Flow<MushafViewerState> = context.dataStore.data.map { prefs ->
        MushafViewerState(
            twoPage = prefs[KEY_MUSHAF_TWO_PAGE] ?: false,
            fillWidth = prefs[KEY_MUSHAF_FILL_WIDTH] ?: false,
            groupAyahs = prefs[KEY_MUSHAF_GROUP_TINT] ?: true,
            barsShown = prefs[KEY_MUSHAF_BARS] ?: false,
            cover = prefs[KEY_MUSHAF_COVER] ?: false,
            coverBarsHidden = prefs[KEY_MUSHAF_COVER_BARS] ?: false
        )
    }

    suspend fun setMushafViewerState(state: MushafViewerState) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MUSHAF_TWO_PAGE] = state.twoPage
            prefs[KEY_MUSHAF_FILL_WIDTH] = state.fillWidth
            prefs[KEY_MUSHAF_GROUP_TINT] = state.groupAyahs
            prefs[KEY_MUSHAF_BARS] = state.barsShown
            prefs[KEY_MUSHAF_COVER] = state.cover
            prefs[KEY_MUSHAF_COVER_BARS] = state.coverBarsHidden
        }
    }

    /** Quiz questions answered wrongly, to ask again on a later day (this device only, never synced). */
    val quizRetries: Flow<List<QuizRetry>> = context.dataStore.data.map { prefs -> QuizRetries.parse(prefs[KEY_QUIZ_RETRIES]) }

    suspend fun editQuizRetries(change: (List<QuizRetry>) -> List<QuizRetry>) {
        context.dataStore.edit { prefs -> prefs[KEY_QUIZ_RETRIES] = QuizRetries.format(change(QuizRetries.parse(prefs[KEY_QUIZ_RETRIES]))) }
    }

    /** The best score in «فوانيس» on this device (0 before the first round). */
    val lanternsBest: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_LANTERNS_BEST] ?: 0 }

    /** How many rounds of «فوانيس» were played. */
    val lanternsRounds: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_LANTERNS_ROUNDS] ?: 0 }

    /** Whether «فوانيس» takes its verses from the hifz (else from Juz 'Amma). */
    val lanternsFromHifz: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_LANTERNS_HIFZ] ?: true }

    suspend fun setLanternsFromHifz(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_LANTERNS_HIFZ] = on }
    }

    /** Counts a finished round of «فوانيس»; true when [score] beat the best (and is kept as the best). */
    suspend fun recordLanternsRound(score: Int): Boolean {
        var beaten = false
        context.dataStore.edit { prefs ->
            val best = prefs[KEY_LANTERNS_BEST] ?: 0
            if (score > best) {
                prefs[KEY_LANTERNS_BEST] = score
                beaten = true
            }
            prefs[KEY_LANTERNS_ROUNDS] = (prefs[KEY_LANTERNS_ROUNDS] ?: 0) + 1
        }
        return beaten
    }

    /** The best stars of each «فوانيس» level won, level 1 first; the next level to play is the one after the last. */
    val lanternsStars: Flow<List<Int>> = context.dataStore.data.map { prefs -> LanternsLevels.parseStars(prefs[KEY_LANTERNS_STARS]) }

    /** Keeps [stars] for [level] if better than before; returns the stars it had before (0 for a level not won yet). */
    suspend fun recordLanternsLevel(level: Int, stars: Int): Int {
        var before = 0
        context.dataStore.edit { prefs ->
            val kept = LanternsLevels.parseStars(prefs[KEY_LANTERNS_STARS])
            before = kept.getOrNull(level - 1) ?: 0
            prefs[KEY_LANTERNS_STARS] = LanternsLevels.formatStars(LanternsLevels.withStars(kept, level, stars))
            prefs[KEY_LANTERNS_ROUNDS] = (prefs[KEY_LANTERNS_ROUNDS] ?: 0) + 1
        }
        return before
    }

    /** Verses slipped on in «فوانيس» (ayah id to the clean builds still needed), the most recent first: they come back first. */
    val lanternsSlips: Flow<Map<Int, Int>> = context.dataStore.data.map { prefs -> GameSlips.parse(prefs[KEY_LANTERNS_SLIPS]) }

    /** Updates the slipped verses after a round in which [played] were played. */
    suspend fun recordLanternsSlips(played: List<PlayedVerse>) {
        if (played.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[KEY_LANTERNS_SLIPS] = GameSlips.format(GameSlips.after(GameSlips.parse(prefs[KEY_LANTERNS_SLIPS]), played))
        }
    }

    /** Verses lately drawn for rounds of «فوانيس» (ayah ids), the most recent first: the next rounds draw others while there are. */
    val lanternsSeen: Flow<List<Int>> = context.dataStore.data.map { prefs -> GameSeen.parse(prefs[KEY_LANTERNS_SEEN]) }

    /** Remembers that a round drew [drawn] (in the order they come). */
    suspend fun recordLanternsSeen(drawn: List<Int>) {
        if (drawn.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[KEY_LANTERNS_SEEN] = GameSeen.format(GameSeen.after(GameSeen.parse(prefs[KEY_LANTERNS_SEEN]), drawn))
        }
    }

    /** The most verses built in one run of «فوانيس» at its hardest (إتقان), on this device; 0 before the first. */
    val lanternsMasteryBest: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_LANTERNS_MASTERY] ?: 0 }

    /** Counts a mastery run that built [verses]; true when that is a new record (and it is kept). */
    suspend fun recordLanternsMastery(verses: Int): Boolean {
        var beaten = false
        context.dataStore.edit { prefs ->
            if (verses > (prefs[KEY_LANTERNS_MASTERY] ?: 0)) {
                prefs[KEY_LANTERNS_MASTERY] = verses
                beaten = true
            }
            prefs[KEY_LANTERNS_ROUNDS] = (prefs[KEY_LANTERNS_ROUNDS] ?: 0) + 1
        }
        return beaten
    }

    /** Counts a round of «فوانيس» that keeps nothing else (a calm sitting). */
    suspend fun countLanternsRound() {
        context.dataStore.edit { prefs -> prefs[KEY_LANTERNS_ROUNDS] = (prefs[KEY_LANTERNS_ROUNDS] ?: 0) + 1 }
    }

    /** Whether «فوانيس» plays its sounds. */
    val lanternsSound: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_LANTERNS_SOUND] ?: true }

    suspend fun setLanternsSound(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_LANTERNS_SOUND] = on }
    }

    /** «فوانيس» draws verses that have look-alikes, each followed by its look-alike (off by default). */
    val lanternsAlikeFocus: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_LANTERNS_ALIKE_FOCUS] ?: false }

    suspend fun setLanternsAlikeFocus(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_LANTERNS_ALIKE_FOCUS] = on }
    }

    /** Every how many minutes of reading in the Mushaf a quiz on the pages read is offered; 0 is never. */
    val readingQuizMinutes: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_READING_QUIZ] ?: READING_QUIZ_DEFAULT }

    suspend fun setReadingQuizMinutes(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_READING_QUIZ] = minutes.coerceIn(0, 240) }
    }

    /** The daily reminder: whether it is on, and its time as minutes after midnight (this device only). */
    val reminder: Flow<ReminderSettings> = context.dataStore.data.map { prefs ->
        ReminderSettings(prefs[KEY_REMINDER_ON] ?: false, prefs[KEY_REMINDER_MINUTES] ?: ReminderSettings.DEFAULT_MINUTES)
    }

    suspend fun setReminder(on: Boolean, minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REMINDER_ON] = on
            prefs[KEY_REMINDER_MINUTES] = minutes.coerceIn(0, 24 * 60 - 1)
            // Answered once, the offer on Today does not come back.
            prefs[KEY_REMINDER_OFFERED] = true
        }
    }

    /** True once the user turned the reminder on or closed its offer on Today. */
    val reminderOfferClosed: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_REMINDER_OFFERED] ?: false }

    suspend fun closeReminderOffer() {
        context.dataStore.edit { prefs -> prefs[KEY_REMINDER_OFFERED] = true }
    }

    /** The last day (local epoch day) whose finished ward was celebrated on Today. */
    val wardCelebratedDay: Flow<Long> = context.dataStore.data.map { prefs -> prefs[KEY_WARD_CELEBRATED] ?: 0L }

    suspend fun setWardCelebrated(day: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_WARD_CELEBRATED] = day }
    }

    /**
     * The milestones already shown (their keys), and whether the ones reached before milestones existed were set aside
     * (they never get a moment of their own).
     */
    val milestonesSeen: Flow<Pair<Boolean, Set<String>>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_MILESTONES_START] ?: false) to prefs[KEY_MILESTONES_SEEN]?.split('\n')?.filter { it.isNotBlank() }.orEmpty().toSet()
    }

    /** Adds [keys] to the milestones shown; [baseline] also records that the ones reached before were set aside. */
    suspend fun addMilestonesSeen(keys: Collection<String>, baseline: Boolean = false) {
        context.dataStore.edit { prefs ->
            val old = prefs[KEY_MILESTONES_SEEN]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
            prefs[KEY_MILESTONES_SEEN] = (old + keys).distinct().takeLast(MILESTONES_KEPT).joinToString("\n")
            if (baseline) prefs[KEY_MILESTONES_START] = true
        }
    }

    /** The latest searches, newest first (this device only, never synced). */
    val searchRecent: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SEARCH_RECENT]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    }

    /** Puts [query] first among the latest searches, keeping the last [RECENT_SEARCHES]. */
    suspend fun addSearchRecent(query: String) {
        val q = query.trim().replace('\n', ' ')
        if (q.isEmpty()) return
        context.dataStore.edit { prefs ->
            val old = prefs[KEY_SEARCH_RECENT]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
            prefs[KEY_SEARCH_RECENT] = (listOf(q) + old.filter { it != q }).take(RECENT_SEARCHES).joinToString("\n")
        }
    }

    suspend fun clearSearchRecent() {
        context.dataStore.edit { prefs -> prefs.remove(KEY_SEARCH_RECENT) }
    }

    /** The reciter the reader chose (see Reciter); empty until they choose. */
    val reciterId: Flow<String> = context.dataStore.data.map { prefs -> prefs[KEY_RECITER] ?: "" }

    suspend fun setReciterId(id: String) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITER] = id }
    }

    /** How many times each verse is played before the next. */
    val recitationRepeat: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_REPEAT] ?: 1 }

    suspend fun setRecitationRepeat(times: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_REPEAT] = times.coerceIn(1, 20) }
    }

    /** Whether the range starts over when it ends. */
    val recitationLoop: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_LOOP] ?: false }

    suspend fun setRecitationLoop(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_LOOP] = on }
    }

    /** Whether playing goes on to the end of the surah instead of stopping at the end of the page. */
    val recitationToSurahEnd: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_SURAH] ?: false }

    suspend fun setRecitationToSurahEnd(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_SURAH] = on }
    }

    /** Playback speed in percent (100 = normal). */
    /** How many times the whole passage plays through before the recitation ends (1 = once). */
    val recitationRunRepeat: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_RUN] ?: 1 }

    suspend fun setRecitationRunRepeat(times: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_RUN] = times.coerceIn(1, 20) }
    }

    /**
     * Recite-after-me: after each verse the recitation waits for this share of the verse's length (in percent) for
     * the reader to say it; 0 means it does not wait.
     */
    val recitationSayAfterPercent: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_AFTER] ?: 0 }

    suspend fun setRecitationSayAfterPercent(percent: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_AFTER] = percent.coerceIn(0, 300) }
    }

    /** Which pages the practice questions are about (see PracticeScope); nothing chosen means every memorised page. */
    val practiceScope: Flow<PracticeScope> = context.dataStore.data.map { prefs ->
        PracticeScope.fromStored(prefs[KEY_PRACTICE_TIERS] ?: 0, prefs[KEY_PRACTICE_LAST] ?: 0, prefs[KEY_PRACTICE_UNIT] ?: 0)
    }

    suspend fun setPracticeScope(scope: PracticeScope) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PRACTICE_TIERS] = scope.tierMask
            prefs[KEY_PRACTICE_LAST] = scope.recentAmount
            prefs[KEY_PRACTICE_UNIT] = scope.recentUnit.code
        }
    }

    /** The verse (global id) the last recitation had reached; 0 when there has been none. */
    val lastRecitationAyah: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_LAST] ?: 0 }

    suspend fun setLastRecitationAyah(id: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_LAST] = id }
    }

    val recitationSpeedPercent: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_RECITE_SPEED] ?: 100 }

    suspend fun setRecitationSpeedPercent(percent: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_RECITE_SPEED] = percent.coerceIn(50, 200) }
    }

    /** The id of the tafsir the reader chose last (see TafsirSource); Saadi until they choose. */
    val tafsirSource: Flow<Int> = context.dataStore.data.map { prefs -> prefs[KEY_TAFSIR_SOURCE] ?: 1 }

    suspend fun setTafsirSource(id: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_TAFSIR_SOURCE] = id }
    }

    /** When the last cloud sync finished, in millis, or null if there has been none. */
    val lastSyncTime: Flow<Long?> = context.dataStore.data.map { prefs -> prefs[KEY_SYNC_TIME] }

    suspend fun setLastSyncTime(millis: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_SYNC_TIME] = millis }
    }

    /** A random id for this installation, written with each cloud push. */
    suspend fun deviceId(): String {
        context.dataStore.data.first()[KEY_DEVICE_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { prefs -> prefs[KEY_DEVICE_ID] = id }
        return id
    }

    /** True once another device has taken the account over; syncing here is paused until the user decides. */
    val sessionDisplaced: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEY_DISPLACED] ?: false }

    suspend fun isSessionDisplaced(): Boolean = context.dataStore.data.first()[KEY_DISPLACED] ?: false

    suspend fun setSessionDisplaced(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DISPLACED] = on }
    }

    /**
     * The account this device is set up to sync with, or null: set only once the user has chosen how this device
     * joins the account and that has worked, cleared by signing out and by anything that ends the link.
     */
    suspend fun syncOwner(): String? = context.dataStore.data.first()[KEY_SYNC_UID]?.takeIf { it.isNotEmpty() }

    val syncOwnerFlow: Flow<String?> = context.dataStore.data.map { prefs -> prefs[KEY_SYNC_UID]?.takeIf { it.isNotEmpty() } }

    /**
     * What the last sync with [uid]'s cloud copy left behind. Another account has no shared history, so for it
     * everything counts as new.
     */
    fun syncMemoryFor(uid: String): SyncMemory = object : SyncMemory {
        override suspend fun lastLocalHash(): String? {
            val prefs = context.dataStore.data.first()
            return if (prefs[KEY_SYNC_UID] == uid) prefs[KEY_SYNC_HASH]?.takeIf { it.isNotEmpty() } else null
        }

        override suspend fun lastRemoteVersion(): Long {
            val prefs = context.dataStore.data.first()
            return if (prefs[KEY_SYNC_UID] == uid) prefs[KEY_SYNC_VERSION] ?: 0L else 0L
        }

        override suspend fun lastSnapshot(): String? {
            if (context.dataStore.data.first()[KEY_SYNC_UID] != uid) return null
            return withContext(Dispatchers.IO) { baseFile().takeIf { it.exists() }?.readText() }
        }

        override suspend fun remember(localHash: String, remoteVersion: Long, snapshot: String) {
            // The copy first: if the app dies in between, the older hash just makes the next sync look again.
            withContext(Dispatchers.IO) {
                val temp = File(baseFile().path + ".tmp")
                temp.writeText(snapshot)
                if (!temp.renameTo(baseFile())) { baseFile().delete(); temp.renameTo(baseFile()) }
            }
            context.dataStore.edit { prefs ->
                prefs[KEY_SYNC_UID] = uid
                prefs[KEY_SYNC_HASH] = localHash
                prefs[KEY_SYNC_VERSION] = remoteVersion
            }
        }
    }

    /** The data as the last sync left it (see SyncMemory). */
    private fun baseFile() = File(context.filesDir, "sync_base.json")

    /**
     * The settings that follow the reader to every device: reciter and playback, practice choices, tafsir and Mushaf
     * theme. What belongs to one device (app lock, screen rotation, where the reader was) is left out.
     */
    suspend fun syncedSettings(): JSONObject {
        val prefs = context.dataStore.data.first()
        return JSONObject().also { out ->
            for (key in SYNCED_INTS) prefs[key]?.let { out.put(key.name, it) }
            for (key in SYNCED_BOOLEANS) prefs[key]?.let { out.put(key.name, it) }
            for (key in SYNCED_STRINGS) prefs[key]?.let { out.put(key.name, it) }
        }
    }

    /** Sets the settings [syncedSettings] wrote; any that are missing from [json] stay as they are. */
    suspend fun applySyncedSettings(json: JSONObject) {
        context.dataStore.edit { prefs ->
            for (key in SYNCED_INTS) if (json.has(key.name)) prefs[key] = json.optInt(key.name)
            for (key in SYNCED_BOOLEANS) if (json.has(key.name)) prefs[key] = json.optBoolean(key.name)
            for (key in SYNCED_STRINGS) if (json.has(key.name)) prefs[key] = json.optString(key.name)
        }
    }

    /** Ends the link with the account: nothing syncs until the user chooses again how this device joins one. */
    suspend fun forgetSyncHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SYNC_UID)
            prefs.remove(KEY_SYNC_HASH)
            prefs.remove(KEY_SYNC_VERSION)
            prefs.remove(KEY_SYNC_TIME)
        }
        withContext(Dispatchers.IO) { baseFile().delete() }
    }

    /**
     * Puts everything stored here back to how a first run finds it: settings, onboarding, the sync link, the lot.
     * Only the installation id stays, so the account does not take this phone for a second device.
     */
    suspend fun resetToFreshInstall() {
        context.dataStore.edit { prefs ->
            val id = prefs[KEY_DEVICE_ID]
            prefs.clear()
            if (id != null) prefs[KEY_DEVICE_ID] = id
        }
        withContext(Dispatchers.IO) { baseFile().delete() }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_FIRST_RUN] = false
        }
    }

    suspend fun markHeatmapCoachmarkSeen() {
        context.dataStore.edit { prefs ->
            prefs[KEY_HEATMAP_COACHMARK] = true
        }
    }
}

/**
 * How the reader left the Mushaf view: two pages or one, the pages filling the width with the screen held sideways (off
 * at first: the whole page is seen), the verses of look-alike groups tinted, the bars shown, cover mode and its bars
 * hidden. Each used to start over on every opening (2026-11-20, the user: "I need to get back to the state I left it
 * last time").
 */
data class MushafViewerState(
    val twoPage: Boolean = false,
    val fillWidth: Boolean = false,
    val groupAyahs: Boolean = true,
    val barsShown: Boolean = false,
    val cover: Boolean = false,
    val coverBarsHidden: Boolean = false
)
