package com.dockeep.app.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import com.dockeep.app.database.Document
import com.dockeep.app.database.DocumentImage
import com.dockeep.app.database.AppDatabase
import com.dockeep.app.database.Person
import com.dockeep.app.utils.FileUtils
import java.io.File
import java.util.Date

class DocumentRepository private constructor(private val database: AppDatabase, private val context: Context) {
    fun getAllDocuments(): LiveData<List<Document>> = database.documentDao().getAllDocuments()
    
    suspend fun getAllDocumentsSync(): List<Document> = database.documentDao().getAllDocumentsSync()
    
    fun searchDocuments(query: String): LiveData<List<Document>> = database.documentDao().searchDocuments("%$query%")

    fun getDocumentById(id: Long): LiveData<Document> = database.documentDao().getDocumentById(id)

    fun getDocumentsForPerson(personId: Long): LiveData<List<Document>> = database.documentDao().getDocumentsForPerson(personId)
    
    suspend fun getDocumentsForPersonSync(personId: Long): List<Document> = database.documentDao().getDocumentsForPersonSync(personId)

    fun getDocumentsForMainUser(): LiveData<List<Document>> = database.documentDao().getDocumentsForMainUser()

    fun getDocumentByName(name: String): LiveData<Document?> = database.documentDao().getDocumentByName(name)

    suspend fun getDocumentByNameSync(name: String): Document? = database.documentDao().getDocumentByNameSync(name)

    suspend fun insertDocument(document: Document): Long {
        // Set the order to the max order + 1 if it's not already set
        if (document.order == 0) {
            val maxOrder = database.documentDao().getMaxOrder() ?: 0
            val documentWithOrder = document.copy(order = maxOrder + 1)
            return database.documentDao().insertDocument(documentWithOrder)
        }
        return database.documentDao().insertDocument(document)
    }

    suspend fun updateDocument(document: Document) {
        val updatedDocument = document.copy(updatedAt = Date())
        database.documentDao().updateDocument(updatedDocument)
    }

    suspend fun deleteDocument(document: Document) {
        // Get all images associated with this document
        val images = getImagesForDocumentSync(document.id)
        
        // Delete all image files from the file system
        for (image in images) {
            val file = File(image.imagePath)
            if (file.exists()) {
                file.delete()
            }
        }
        
        // Delete all images associated with this document from database
        database.documentDao().deleteAllImagesForDocument(document.id)
        
        // Delete the document folder
        FileUtils.deleteDocumentFolder(context, document.name)
        
        // Delete the document from database
        database.documentDao().deleteDocument(document)
    }

    suspend fun updateDocumentOrder(documents: List<Document>) {
        // Create a copy of the list to avoid ConcurrentModificationException
        val documentsCopy = documents.toList()
        // Update the order of all documents
        for ((index, document) in documentsCopy.withIndex()) {
            val updatedDocument = document.copy(order = index)
            database.documentDao().updateDocument(updatedDocument)
        }
    }

    fun getImagesForDocument(documentId: Long): LiveData<List<DocumentImage>> = 
        database.documentDao().getImagesForDocument(documentId)

    suspend fun getImagesForDocumentSync(documentId: Long): List<DocumentImage> = 
        database.documentDao().getImagesForDocumentSync(documentId)

    suspend fun insertImage(image: DocumentImage) = database.documentDao().insertImage(image)

    suspend fun updateImage(image: DocumentImage) = database.documentDao().updateImage(image)

    suspend fun updateImageOrder(images: List<DocumentImage>) {
        // Create a copy of the list to avoid ConcurrentModificationException
        val imagesCopy = images.toList()
        // Update each image with its new order
        for (image in imagesCopy) {
            try {
                database.documentDao().updateImage(image)
            } catch (e: Exception) {
                // Log the error
                android.util.Log.e("DocumentRepository", "Error updating image order for image ${image.id}", e)
            }
        }
    }

    suspend fun deleteImage(image: DocumentImage) {
        // Delete the actual file
        val file = File(image.imagePath)
        if (file.exists()) {
            file.delete()
        }
        
        // Delete from database
        database.documentDao().deleteImage(image)
    }

    suspend fun addImagesToDocument(documentId: Long, imageUris: List<Uri>): List<DocumentImage> {
        val documentImages = mutableListOf<DocumentImage>()
        
        // Get the current max order for this document
        var maxOrder = 0
        val existingImages = getImagesForDocumentSync(documentId)
        if (existingImages.isNotEmpty()) {
            maxOrder = existingImages.maxByOrNull { it.order }?.order ?: 0
        }
        
        // Create a copy of the list to avoid ConcurrentModificationException
        val imageUrisCopy = imageUris.toList()
        for ((index, uri) in imageUrisCopy.withIndex()) {
            val imagePath = FileUtils.saveImageToDocumentFolder(context, documentId, uri)
            if (imagePath != null) {
                val documentImage = DocumentImage(
                    documentId = documentId,
                    imagePath = imagePath,
                    order = maxOrder + index + 1
                )
                insertImage(documentImage)
                documentImages.add(documentImage)
            }
        }
        
        return documentImages
    }

    /**
     * Clean up orphaned image entries in the database where the actual file no longer exists
     */
    suspend fun cleanupOrphanedImageEntries() {
        // Get all images from the database
        val allImages = database.documentDao().getAllImagesSync()
        
        // Create a copy of the list to avoid ConcurrentModificationException
        val allImagesCopy = allImages.toList()
        // Check each image to see if its file exists
        for (image in allImagesCopy) {
            val imageFile = File(image.imagePath)
            if (!imageFile.exists()) {
                // File doesn't exist, remove the database entry
                database.documentDao().deleteImage(image)
                Log.d("DocumentRepository", "Cleaned up orphaned image entry: ${image.imagePath}")
            }
        }
    }

    suspend fun getPersonByIdSync(personId: Long): Person? = database.personDao().getPersonByIdSync(personId)

    suspend fun getPersonByNameSync(name: String): Person? = database.personDao().getPersonByNameSync(name)

    suspend fun getAllPeopleSync(): List<Person> = database.personDao().getAllPeopleSync()

    suspend fun insertPerson(person: Person): Long = database.personDao().insert(person)

    suspend fun getDocumentByNameAndPersonSync(name: String, personId: Long): Document? = database.documentDao().getDocumentByNameAndPersonSync(name, personId)

    companion object {
        @Volatile
        private var INSTANCE: DocumentRepository? = null

        fun getInstance(database: AppDatabase, context: Context): DocumentRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DocumentRepository(database, context)
                INSTANCE = instance
                instance
            }
        }
    }
}