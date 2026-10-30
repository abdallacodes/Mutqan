package com.example.mutqan.domain

/**
 * What a revision session demonstrated, as reported by the user. Only the first four count as
 * recall evidence; [PRACTICE_ONLY] (reading or listening) is kept in the history but never feeds
 * the memory model or a passage's state.
 *
 * [code] is what the database stores, so existing codes must never change. [quality] is the
 * 0..1 value the FSRS model replays for the outcome.
 */
enum class RecallOutcome(val code: Int, val quality: Float) {
    PRACTICE_ONLY(0, 0f),
    INDEPENDENT(1, 0.95f),
    SELF_CORRECTED(2, 0.8f),
    HINTED(3, 0.55f),
    UNABLE(4, 0.25f);

    /** True when the session is evidence of recall, whatever its result. */
    val isRecallAttempt: Boolean get() = this != PRACTICE_ONLY

    /** True when the passage was recalled without being shown it (a self-correction included). */
    val isSuccess: Boolean get() = this == INDEPENDENT || this == SELF_CORRECTED

    companion object {
        fun fromCode(code: Int): RecallOutcome = entries.firstOrNull { it.code == code } ?: INDEPENDENT

        /**
         * The outcome closest to a legacy 0..1 quality, used when old logs (which only had the
         * slider) are migrated or imported. The bands sit just under 0.9 / 0.7 / 0.4 because a
         * slider value such as 0.9f is stored as 0.89999998..., and must still land in its band.
         * `MIGRATION_15_16` applies the same bands in SQL.
         */
        fun fromQuality(quality: Float): RecallOutcome = when {
            quality >= 0.89f -> INDEPENDENT
            quality >= 0.69f -> SELF_CORRECTED
            quality >= 0.39f -> HINTED
            else -> UNABLE
        }
    }
}

/** The kinds of mistake a user can tag a session with; stored as a bit set in one integer. */
enum class MistakeTag(val bit: Int) {
    OMISSION(1),
    ADDITION(2),
    SUBSTITUTION(4),
    ORDER(8),
    HESITATION(16),
    REPETITION(32),
    PROMPT_NEEDED(64),
    SIMILAR_AYAH(128);

    companion object {
        fun fromMask(mask: Int): Set<MistakeTag> = entries.filterTo(LinkedHashSet()) { mask and it.bit != 0 }

        fun toMask(tags: Collection<MistakeTag>): Int = tags.fold(0) { acc, tag -> acc or tag.bit }
    }
}
