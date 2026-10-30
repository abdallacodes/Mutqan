package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityLinksTest {

    @Test
    fun twoMembersOnDifferentPagesLinkBothWays() {
        val links = SimilarityLinks.pageLinks(mapOf(1 to listOf(5..5, 7..7)))
        assertEquals(setOf(PageSimilarityLink(5, 7), PageSimilarityLink(7, 5)), links.toSet())
    }

    @Test
    fun membersOnTheSamePageProduceNoLink() {
        assertTrue(SimilarityLinks.pageLinks(mapOf(1 to listOf(5..5, 5..5))).isEmpty())
    }

    @Test
    fun anAyahOverAPageBreakLinksBothOfItsPagesButNotToItself() {
        val links = SimilarityLinks.pageLinks(mapOf(1 to listOf(120..121, 300..300))).toSet()
        assertEquals(
            setOf(
                PageSimilarityLink(120, 300), PageSimilarityLink(121, 300),
                PageSimilarityLink(300, 120), PageSimilarityLink(300, 121)
            ),
            links
        )
    }

    @Test
    fun groupsAreIndependentAndDuplicatesCollapse() {
        val links = SimilarityLinks.pageLinks(
            mapOf(1 to listOf(2..2, 3..3), 2 to listOf(2..2, 3..3), 3 to listOf(9..9))
        )
        assertEquals(2, links.size)
    }
}
