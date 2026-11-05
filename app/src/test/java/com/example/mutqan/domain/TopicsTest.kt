package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsTest {

    private fun topic(
        id: Int, name: String, arabic: String = "", thematic: Int = -1, ontology: Int = -1, parent: Int = -1,
        verses: IntArray = intArrayOf()
    ) = Topic(id, name, arabic, thematic, ontology, parent, "", intArrayOf(), verses)

    private val index = TopicIndex(
        listOf(
            topic(1, "Stories", "القصص"),
            topic(2, "Prophets", "الأنبياء", thematic = 1, verses = intArrayOf(5, 6, 7)),
            topic(3, "Patience", "الصبر", thematic = 1, verses = intArrayOf(6, 8)),
            topic(4, "Event"),
            topic(5, "Battle", ontology = 4, verses = intArrayOf(7)),
            topic(6, "Lone", parent = 3, verses = intArrayOf(9, 10, 11, 12))
        )
    )

    private val arabic = TopicLanguage(arabicUi = true)
    private val english = TopicLanguage(arabicUi = false)

    @Test
    fun `roots are the tops of the thematic tree and of the ontology`() {
        assertEquals(listOf(1), index.themeRoots.map { it.id })
        assertEquals(listOf(4), index.conceptRoots.map { it.id })
    }

    @Test
    fun `children join all three hierarchies`() {
        assertEquals(listOf(2, 3), index.childrenOf(1).map { it.id })
        assertEquals(listOf(5), index.childrenOf(4).map { it.id })
        assertEquals(listOf(6), index.childrenOf(3).map { it.id })
    }

    @Test
    fun `the topics of a verse come most specific first`() {
        assertEquals(listOf(3, 2), index.topicsOf(6).map { it.id })
        assertTrue(index.topicsOf(99).isEmpty())
    }

    @Test
    fun `the Arabic interface lists only Arabic-named topics, and no English-only themes`() {
        val english = index[5]!!
        assertFalse(arabic.visible(english))
        assertFalse(arabic.themesVisible())
        assertTrue(this.english.themesVisible())
        assertTrue(this.english.visible(english))
        assertEquals("الصبر", arabic.title(index[3]!!))
        assertEquals("Battle", arabic.title(english))
        assertEquals("Patience", this.english.title(index[3]!!))
        assertEquals("الصبر", this.english.secondary(index[3]!!))
    }

    @Test
    fun `search finds English and Arabic names, widest topic first`() {
        assertEquals(listOf(3), index.search("pati", english, 10).map { it.id })
        assertEquals(listOf(2), index.search("الأنبياء", arabic, 10).map { it.id })
        assertEquals(listOf(3), index.search("صبر", arabic, 10).map { it.id })
        assertTrue(index.search("Battle", arabic, 10).isEmpty())
        assertTrue(index.search("  ", english, 10).isEmpty())
    }

    @Test
    fun `popular topics leave out the roots and the empty ones`() {
        assertEquals(listOf(3, 6, 2, 5), index.popular(english, 10).map { it.id })
    }

    @Test
    fun `a topic counts the verses of everything under it, each once, in reading order`() {
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12), index.versesOf(index[1]!!).toList())
        assertEquals(listOf(6, 8, 9, 10, 11, 12), index.versesOf(index[3]!!).toList())
        assertEquals(listOf(7), index.versesOf(index[4]!!).toList())
    }

    @Test
    fun `a topic with no verses of its own is still found when its children have some`() {
        assertEquals(listOf(1), index.search("Stories", english, 5).map { it.id })
    }
}
