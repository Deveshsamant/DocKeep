package com.dockeep.app

import com.dockeep.app.utils.ExportNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export/import round trip for text blocks.
 *
 * Worth testing directly because the failure is silent: notes were dropped
 * from the archive entirely, so a backup looked like it had worked and the
 * loss only showed up after the vault had been restored.
 */
class ExportNotesTest {

    @Test
    fun `a written name reads back as the same order`() {
        for (order in listOf(0, 1, 7, 42, 999)) {
            val name = ExportNotes.fileNameFor(order, 0)
            assertEquals(order, ExportNotes.orderOf(name))
        }
    }

    @Test
    fun `order is zero padded so names sort in block order`() {
        val names = listOf(10, 2, 1).map { ExportNotes.fileNameFor(it, 0) }.sorted()
        assertEquals(
            listOf(1, 2, 10),
            names.map { ExportNotes.orderOf(it) }
        )
    }

    @Test
    fun `two notes at the same order do not collide`() {
        val first = ExportNotes.fileNameFor(3, 0)
        val second = ExportNotes.fileNameFor(3, 1)
        assertTrue(first != second)
        assertEquals(3, ExportNotes.orderOf(first))
        assertEquals(3, ExportNotes.orderOf(second))
    }

    @Test
    fun `a scan is not mistaken for a note`() {
        assertNull(ExportNotes.orderOf("12-20260910-441.jpg"))
        assertNull(ExportNotes.orderOf("IMG_0001.png"))
        assertFalse(ExportNotes.isNote("Passport.pdf"))
    }

    @Test
    fun `a text file the user added themselves is left alone`() {
        // Importing this as a note would invent a block; it is not ours.
        assertNull(ExportNotes.orderOf("readme.txt"))
        assertNull(ExportNotes.orderOf("notes.txt"))
    }

    @Test
    fun `a malformed name is rejected rather than defaulting to zero`() {
        assertNull(ExportNotes.orderOf("dockeep-note-abc-0.txt"))
        assertNull(ExportNotes.orderOf("dockeep-note-.txt"))
    }

    @Test
    fun `the name survives being read out of a zip path`() {
        val name = ExportNotes.fileNameFor(5, 2)
        assertEquals(5, ExportNotes.orderOf("Devesh's Passport/$name"))
        assertEquals(5, ExportNotes.orderOf("FriendsAndFamily/Amit/Aadhaar/$name"))
    }
}
