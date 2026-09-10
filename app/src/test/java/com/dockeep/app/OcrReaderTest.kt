package com.dockeep.app

import com.dockeep.app.utils.OcrReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The name suggestion is pure text handling, so it can be exercised properly
 * off-device. These use the shape of text ML Kit actually returns from Indian
 * identity documents — the case this feature exists for.
 */
class OcrReaderTest {

    @Test
    fun suggestName_returnsNullForNothing() {
        assertNull(OcrReader.suggestName(null))
        assertNull(OcrReader.suggestName(""))
        assertNull(OcrReader.suggestName("   \n  \n"))
    }

    @Test
    fun suggestName_skipsGovernmentBoilerplate() {
        val text = """
            GOVERNMENT OF INDIA
            INCOME TAX DEPARTMENT
            ABCDE1234F
        """.trimIndent()

        val suggestion = OcrReader.suggestName(text)
        assertEquals("Income Tax Department", suggestion)
    }

    @Test
    fun suggestName_ignoresLinesThatAreMostlyDigits() {
        val text = """
            1234 5678 9012
            Permanent Account Number
            07/11/1998
        """.trimIndent()

        assertEquals("Permanent Account Number", OcrReader.suggestName(text))
    }

    @Test
    fun suggestName_titleCasesTheResult() {
        val suggestion = OcrReader.suggestName("DRIVING LICENCE\n99887766")
        assertEquals("Driving Licence", suggestion)
    }

    @Test
    fun suggestName_ignoresLinesTooShortToBeATitle() {
        // "PAN" is under the four-character floor, so the longer line wins.
        assertEquals("Account Details", OcrReader.suggestName("PAN\nAccount Details"))
    }

    @Test
    fun suggestName_capsLength() {
        val long = "A".repeat(10) + " " + "B".repeat(60)
        val suggestion = OcrReader.suggestName(long)
        // Either it rejects the over-long line outright or it truncates, but
        // it must never hand back something unusable as a document name.
        assertTrue(suggestion == null || suggestion.length <= 40)
    }

    @Test
    fun suggestName_skipsUrls() {
        val text = """
            https://incometax.gov.in
            Taxpayer Identity Card
        """.trimIndent()

        assertEquals("Taxpayer Identity Card", OcrReader.suggestName(text))
    }
}
