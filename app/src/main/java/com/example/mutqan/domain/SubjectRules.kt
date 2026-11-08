package com.example.mutqan.domain

import com.example.mutqan.data.local.entity.UserSubjectEntity

/** What saving a subject writes: the row to remove (if any) and the row to insert (id 0) or update. */
data class SubjectWrite(val delete: UserSubjectEntity?, val write: UserSubjectEntity)

/**
 * A verse starts at most one subject. Given the subject now starting at the chosen verse ([atTarget]) and the one being
 * edited ([replacing], null for a new subject), says what to write so that stays true:
 * - a new subject at a verse that has one renames that one instead of adding a second;
 * - an edit moved onto another subject's verse takes its place (the other one is removed);
 * - otherwise it is a plain insert or update.
 */
object SubjectRules {
    fun plan(text: String, ayahId: Int, atTarget: UserSubjectEntity?, replacing: UserSubjectEntity?): SubjectWrite {
        if (replacing == null) {
            val row = atTarget?.copy(subjectText = text)
                ?: UserSubjectEntity(subjectText = text, startAyahId = ayahId, orderIndex = 0)
            return SubjectWrite(delete = null, write = row)
        }
        val other = atTarget?.takeIf { it.id != replacing.id }
        return SubjectWrite(delete = other, write = replacing.copy(subjectText = text, startAyahId = ayahId))
    }

    /** The subjects of a file or snapshot with one per start verse: the first of each verse stays. */
    fun onePerVerse(subjects: List<UserSubjectEntity>): List<UserSubjectEntity> =
        subjects.distinctBy { it.startAyahId }
}
