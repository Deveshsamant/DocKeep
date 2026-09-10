package com.dockeep.app.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY `order` ASC, updatedAt DESC")
    fun getAllDocuments(): LiveData<List<Document>>
    
    @Query("SELECT * FROM documents ORDER BY `order` ASC, updatedAt DESC")
    suspend fun getAllDocumentsSync(): List<Document>

    @Query("SELECT * FROM documents WHERE name LIKE :searchQuery AND personId IS NULL ORDER BY `order` ASC, updatedAt DESC")
    fun searchDocuments(searchQuery: String): LiveData<List<Document>>

    /**
     * Full-text search: matches the document's name, the text of any note
     * inside it, and any text OCR read off its scans.
     *
     * DISTINCT because a query hitting several blocks of one document must
     * still return that document once.
     */
    @Query(
        "SELECT DISTINCT d.* FROM documents d " +
            "LEFT JOIN document_images i ON i.documentId = d.id " +
            "WHERE d.personId IS NULL AND (" +
            "  d.name LIKE :q ESCAPE '\\' " +
            "  OR i.text LIKE :q ESCAPE '\\' " +
            "  OR i.ocrText LIKE :q ESCAPE '\\'" +
            ") " +
            "ORDER BY d.`order` ASC, d.updatedAt DESC"
    )
    fun searchDocumentsFullText(q: String): LiveData<List<Document>>

    /** Scans that have not been read yet, oldest first. */
    @Query(
        "SELECT * FROM document_images " +
            "WHERE blockType = 'IMAGE' AND ocrText IS NULL " +
            "ORDER BY createdAt ASC LIMIT :limit"
    )
    suspend fun getUnreadScans(limit: Int): List<DocumentImage>

    @Query("UPDATE document_images SET ocrText = :text WHERE id = :imageId")
    suspend fun setOcrText(imageId: Long, text: String)

    /**
     * Forgets the text cached for a scan, so the indexer reads it again.
     *
     * Needed after the pixels change: a redacted number is gone from the
     * image, but the text recognised before the edit still holds it, and that
     * copy is what search and "Read text" work from.
     */
    @Query("UPDATE document_images SET ocrText = NULL WHERE id = :imageId")
    suspend fun clearOcrText(imageId: Long)

    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocumentById(id: Long): LiveData<Document>

    @Query("SELECT * FROM documents WHERE personId = :personId ORDER BY `order` ASC, updatedAt DESC")
    fun getDocumentsForPerson(personId: Long): LiveData<List<Document>>
    
    @Query("SELECT * FROM documents WHERE personId = :personId ORDER BY `order` ASC, updatedAt DESC")
    suspend fun getDocumentsForPersonSync(personId: Long): List<Document>

    @Query("SELECT * FROM documents WHERE personId IS NULL ORDER BY `order` ASC, updatedAt DESC")
    fun getDocumentsForMainUser(): LiveData<List<Document>>

    @Query("SELECT * FROM documents WHERE name = :name LIMIT 1")
    fun getDocumentByName(name: String): LiveData<Document?>

    @Query("SELECT * FROM documents WHERE name = :name LIMIT 1")
    suspend fun getDocumentByNameSync(name: String): Document?

    @Query("SELECT * FROM documents WHERE name = :name AND personId = :personId LIMIT 1")
    suspend fun getDocumentByNameAndPersonSync(name: String, personId: Long): Document?

    @Insert
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document)

    @Delete
    suspend fun deleteDocument(document: Document)

    @Query("SELECT * FROM document_images WHERE documentId = :documentId ORDER BY `order` ASC, createdAt ASC")
    fun getImagesForDocument(documentId: Long): LiveData<List<DocumentImage>>

    @Query("SELECT * FROM document_images WHERE documentId = :documentId ORDER BY `order` ASC, createdAt ASC")
    suspend fun getImagesForDocumentSync(documentId: Long): List<DocumentImage>

    @Query("SELECT * FROM document_images")
    suspend fun getAllImagesSync(): List<DocumentImage>

    @Insert
    suspend fun insertImage(image: DocumentImage)

    @Update
    suspend fun updateImage(image: DocumentImage)

    @Delete
    suspend fun deleteImage(image: DocumentImage)

    @Query("DELETE FROM document_images WHERE documentId = :documentId")
    suspend fun deleteAllImagesForDocument(documentId: Long)
    
    @Query("SELECT MAX(`order`) FROM documents")
    suspend fun getMaxOrder(): Int?
}