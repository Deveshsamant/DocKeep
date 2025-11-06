package com.dockeep.app

import com.dockeep.app.database.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.*

class DocumentTest {

    @Test
    fun document_creation_setsDefaultValues() {
        val name = "Test Document"
        val document = Document(name = name)
        
        assertEquals("Document name should match", name, document.name)
        assertNotNull("Created at should not be null", document.createdAt)
        assertNotNull("Updated at should not be null", document.updatedAt)
        assertEquals("Color index should default to 0", 0, document.colorIndex)
        assertEquals("Thumbnail path should default to null", null, document.thumbnailPath)
    }

    @Test
    fun document_creation_withCustomValues() {
        val name = "Test Document"
        val createdAt = Date()
        val updatedAt = Date()
        val colorIndex = 5
        val thumbnailPath = "/path/to/thumbnail.jpg"
        
        val document = Document(
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            colorIndex = colorIndex,
            thumbnailPath = thumbnailPath
        )
        
        assertEquals("Document name should match", name, document.name)
        assertEquals("Created at should match", createdAt, document.createdAt)
        assertEquals("Updated at should match", updatedAt, document.updatedAt)
        assertEquals("Color index should match", colorIndex, document.colorIndex)
        assertEquals("Thumbnail path should match", thumbnailPath, document.thumbnailPath)
    }
}