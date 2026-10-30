package com.example.mutqan.domain

import com.example.mutqan.data.local.entity.SavedPassageEntity

/** What a saved passage is for. [code] is stored; never renumber. */
enum class SavedKind(val code: Int) {
    BOOKMARK(0),
    WEAK(1);

    companion object {
        fun fromCode(code: Int): SavedKind = entries.firstOrNull { it.code == code } ?: BOOKMARK
    }
}

/** Why a passage is weak; stored as a bit set in one integer. */
enum class WeakTag(val bit: Int) {
    FORGETS_BEGINNING(1),
    WEAK_TRANSITION(2),
    SIMILAR_AYAH(4),
    OMISSION(8),
    HESITATION(16),
    UNCERTAIN_WORDING(32);

    companion object {
        fun fromMask(mask: Int): Set<WeakTag> = entries.filterTo(LinkedHashSet()) { mask and it.bit != 0 }

        fun toMask(tags: Collection<WeakTag>): Int = tags.fold(0) { acc, tag -> acc or tag.bit }
    }
}

/** A bookmark or weak passage as the app uses it. [wordId] is 0 when the whole ayah is meant. */
data class SavedPassage(
    val id: Int = 0,
    val kind: SavedKind,
    val startAyahId: Int,
    val endAyahId: Int = startAyahId,
    val wordId: Int = 0,
    val tags: Set<WeakTag> = emptySet(),
    val priority: Int = PRIORITY_NORMAL,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long = 0L
) {
    val isResolved: Boolean get() = resolvedAt > 0L

    fun toEntity() = SavedPassageEntity(
        id = id,
        kind = kind.code,
        startAyahId = startAyahId,
        endAyahId = endAyahId,
        wordId = wordId,
        // Tags and "solid again" only describe a weak passage.
        tags = if (kind == SavedKind.WEAK) WeakTag.toMask(tags) else 0,
        priority = priority.coerceIn(PRIORITY_LOW, PRIORITY_HIGH),
        note = note.trim(),
        createdAt = createdAt,
        resolvedAt = if (kind == SavedKind.WEAK) resolvedAt else 0L
    )

    companion object {
        const val PRIORITY_LOW = 1
        const val PRIORITY_NORMAL = 2
        const val PRIORITY_HIGH = 3

        fun fromEntity(e: SavedPassageEntity) = SavedPassage(
            id = e.id,
            kind = SavedKind.fromCode(e.kind),
            startAyahId = e.startAyahId,
            endAyahId = e.endAyahId,
            wordId = e.wordId,
            tags = WeakTag.fromMask(e.tags),
            priority = e.priority,
            note = e.note,
            createdAt = e.createdAt,
            resolvedAt = e.resolvedAt
        )
    }
}
