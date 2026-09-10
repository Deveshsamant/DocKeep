package com.dockeep.app

import com.dockeep.app.utils.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette has one entry per letter, A–Z. These tests previously asserted a
 * range of 0..7, which was left over from an earlier eight-colour palette and
 * had been failing ever since it grew to 26.
 */
class ColorUtilsTest {

    private val validRange = 0 until ColorUtils.PALETTE_SIZE

    @Test
    fun getColorIndexForName_returnsConsistentIndex() {
        val name = "Test Document"
        val index1 = ColorUtils.getColorIndexForName(name)
        val index2 = ColorUtils.getColorIndexForName(name)

        assertEquals("Color index should be consistent", index1, index2)
        assertTrue("Color index should be within valid range", index1 in validRange)
    }

    @Test
    fun getColorIndexForName_mapsLetterToItsOwnSlot() {
        assertEquals(0, ColorUtils.getColorIndexForName("Aadhaar Card"))
        assertEquals(15, ColorUtils.getColorIndexForName("PAN Card"))
        assertEquals(25, ColorUtils.getColorIndexForName("Zoning Permit"))
    }

    @Test
    fun getColorIndexForName_isCaseInsensitive() {
        assertEquals(
            ColorUtils.getColorIndexForName("passport"),
            ColorUtils.getColorIndexForName("PASSPORT")
        )
    }

    @Test
    fun getColorIndexForName_handlesEmptyString() {
        assertTrue(
            "Color index should be valid for empty string",
            ColorUtils.getColorIndexForName("") in validRange
        )
    }

    @Test
    fun getColorIndexForName_handlesSpecialCharacters() {
        val index = ColorUtils.getColorIndexForName("Test@#$%^&*()Document")
        assertTrue("Color index should be valid for special characters", index in validRange)
    }

    @Test
    fun getColorIndexForName_handlesNamesNotStartingWithALetter() {
        // "1099 Form" used to yield -16, which was then stored on the document.
        for (name in listOf("1099 Form", "@home", "#tax", "  ", "₹ receipts")) {
            val index = ColorUtils.getColorIndexForName(name)
            assertTrue("Index for \"$name\" should be in range but was $index", index in validRange)
        }
    }
}
