package com.dockeep.app

import com.dockeep.app.utils.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A broken LIKE escape still returns rows, just the wrong ones, so it fails
 * quietly. These pin the behaviour down.
 */
class SearchQueryTest {

    @Test
    fun ordinaryTextIsUntouched() {
        assertEquals("passport", SearchQuery.escapeForLike("passport"))
        assertEquals("PAN Card", SearchQuery.escapeForLike("PAN Card"))
    }

    @Test
    fun percentIsEscaped() {
        // Unescaped, this matched every document in the vault.
        assertEquals("\\%", SearchQuery.escapeForLike("%"))
        assertEquals("50\\% off", SearchQuery.escapeForLike("50% off"))
    }

    @Test
    fun underscoreIsEscaped() {
        // Unescaped, this matched any single character.
        assertEquals("\\_", SearchQuery.escapeForLike("_"))
        assertEquals("tax\\_2024", SearchQuery.escapeForLike("tax_2024"))
    }

    @Test
    fun backslashIsEscapedFirstSoAddedOnesAreNotDoubled() {
        assertEquals("\\\\", SearchQuery.escapeForLike("\\"))
        assertEquals("\\\\\\%", SearchQuery.escapeForLike("\\%"))
    }

    @Test
    fun containsPatternWrapsInWildcards() {
        assertEquals("%passport%", SearchQuery.containsPattern("passport"))
        // The wrapping wildcards stay operators; only the user's do not.
        assertEquals("%50\\%%", SearchQuery.containsPattern("50%"))
    }

    @Test
    fun emptyQueryStillProducesAValidPattern() {
        assertEquals("%%", SearchQuery.containsPattern(""))
    }
}
