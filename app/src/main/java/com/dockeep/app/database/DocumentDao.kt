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