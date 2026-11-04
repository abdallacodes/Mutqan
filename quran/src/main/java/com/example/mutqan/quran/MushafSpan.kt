package com.example.mutqan.quran

/** One piece of a [MushafSpan]: a word of the Mushaf, or a mark that is not Quran text (a blank, an ellipsis, a label). */
sealed interface SpanPiece {
    data class Word(val word: QuranWord) : SpanPiece
    data class Mark(val text: String) : SpanPiece
}

/**
 * A stretch of Quran text that can be drawn as the Mushaf prints it. Each word keeps its glyph and the page whose font
 * that glyph belongs to; [plain] is the same stretch as ordinary text, for comparing and for showing while a font is
 * still being fetched.
 */
class MushafSpan(val pieces: List<SpanPiece>) {

    /** The pages whose fonts are needed to draw the words. */
    val pages: Set<Int> = pieces.mapNotNullTo(linkedSetOf()) { (it as? SpanPiece.Word)?.word?.page?.takeIf { p -> p > 0 } }

    /** The Quran words in the span, in order. */
    val words: List<QuranWord> get() = pieces.mapNotNull { (it as? SpanPiece.Word)?.word }

    /** The span as plain text: Hafs words and marks, one space apart. */
    val plain: String = pieces.joinToString(" ") {
        when (it) {
            is SpanPiece.Word -> it.word.hafs
            is SpanPiece.Mark -> it.text
        }
    }

    val isEmpty: Boolean get() = pieces.isEmpty()

    operator fun plus(other: MushafSpan) = MushafSpan(pieces + other.pieces)

    override fun equals(other: Any?) = other is MushafSpan && other.pieces == pieces
    override fun hashCode() = pieces.hashCode()
    override fun toString() = plain

    companion object {
        const val BLANK = "_____"
        const val ELLIPSIS = "…"

        val Empty = MushafSpan(emptyList())

        fun of(words: List<QuranWord>) = MushafSpan(words.map { SpanPiece.Word(it) })

        /** Text that is not Quran, such as the name of a topic. */
        fun label(text: String) = MushafSpan(listOf(SpanPiece.Mark(text)))

        val Blank get() = label(BLANK)
        val More get() = label(ELLIPSIS)
    }
}
