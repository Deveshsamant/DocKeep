package com.dockeep.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dockeep.app.database.AppDatabase
import com.dockeep.app.database.Document
import com.dockeep.app.database.DocumentImage
import com.dockeep.app.database.DocumentTag
import com.dockeep.app.database.Person
import com.dockeep.app.database.Tag
import com.dockeep.app.repository.DocumentRepository
import kotlinx.coroutines.launch

class DocumentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DocumentRepository = DocumentRepository.getInstance(
        AppDatabase.getDatabase(application),
        application
    )

    fun getAllDocuments(): LiveData<List<Document>> {
        return repository.getAllDocuments()
    }

    suspend fun getAllDocumentsSync(): List<Document> {
        return repository.getAllDocumentsSync()
    }

    fun getDocumentById(id: Long): LiveData<Document> {
        return repository.getDocumentById(id)
    }

    fun getDocumentsForPerson(personId: Long): LiveData<List<Document>> {
        return repository.getDocumentsForPerson(personId)
    }

    suspend fun getDocumentsForPersonSync(personId: Long): List<Document> {
        return repository.getDocumentsForPersonSync(personId)
    }

    fun getDocumentsForMainUser(): LiveData<List<Document>> {
        return repository.getDocumentsForMainUser()
    }

    fun searchDocuments(query: String): LiveData<List<Document>> {
        return repository.searchDocuments(query)
    }

    fun getDocumentByName(name: String): LiveData<Document?> {
        return repository.getDocumentByName(name)
    }

    suspend fun getDocumentByNameSync(name: String): Document? {
        return repository.getDocumentByNameSync(name)
    }

    suspend fun insertDocumentSync(document: Document): Long {
        return repository.insertDocument(document)
    }

    fun insertDocument(document: Document) = viewModelScope.launch {
        repository.insertDocument(document)
    }

    suspend fun insertImageSync(image: DocumentImage) {
        repository.insertImage(image)
    }

    suspend fun updateDocumentSync(document: Document) = repository.updateDocument(document)

    suspend fun deleteDocumentSync(document: Document) = repository.deleteDocument(document)

    fun updateDocument(document: Document) = viewModelScope.launch {
        repository.updateDocument(document)
    }

    fun deleteDocument(document: Document) = viewModelScope.launch {
        repository.deleteDocument(document)
    }

    suspend fun deleteImageSync(image: DocumentImage) {
        repository.deleteImage(image)
    }

    suspend fun updateImage(image: DocumentImage) = repository.updateImage(image)

    fun updateImageOrder(images: List<DocumentImage>) = viewModelScope.launch {
        try {
            repository.updateImageOrder(images)
        } catch (e: Exception) {
            // Log the error
            android.util.Log.e("DocumentViewModel", "Error updating image order", e)
        }
    }

    fun updateDocumentOrder(documents: List<Document>) = viewModelScope.launch {
        repository.updateDocumentOrder(documents)
    }

    suspend fun getPersonByIdSync(personId: Long): Person? = repository.getPersonByIdSync(personId)

    suspend fun getPersonByNameSync(name: String): Person? = repository.getPersonByNameSync(name)

    suspend fun getAllPeopleSync(): List<Person> = repository.getAllPeopleSync()

    suspend fun insertPersonSync(person: Person): Long = repository.insertPerson(person)

    suspend fun getDocumentByNameAndPersonSync(name: String, personId: Long): Document? = repository.getDocumentByNameAndPersonSync(name, personId)

    fun getImagesForDocument(documentId: Long): LiveData<List<DocumentImage>> {
        return repository.getImagesForDocument(documentId)
    }

    suspend fun getImagesForDocumentSync(documentId: Long): List<DocumentImage> {
        return repository.getImagesForDocumentSync(documentId)
    }

    suspend fun cleanupOrphanedImageEntriesSync() = repository.cleanupOrphanedImageEntries()

    // ── Search and OCR ──────────────────────────────────────────────────

    /** Matches names, notes and text read off scans. */
    fun searchDocumentsFullText(query: String): LiveData<List<Document>> =
        repository.searchDocumentsFullText(query)

    /** Reads a batch of unread scans in the background. */
    fun indexUnreadScans() = viewModelScope.launch {
        runCatching { repository.indexUnreadScans() }
    }

    suspend fun readScanText(image: DocumentImage): String? = repository.readScanText(image)

    suspend fun invalidateOcr(imageId: Long) = repository.invalidateOcr(imageId)

    // ── Tags ────────────────────────────────────────────────────────────

    fun getAllTags(): LiveData<List<Tag>> = repository.getAllTags()

    suspend fun getAllTagsSync(): List<Tag> = repository.getAllTagsSync()

    suspend fun getTagsForDocumentSync(documentId: Long): List<Tag> =
        repository.getTagsForDocumentSync(documentId)

    suspend fun getAllTagPairingsSync(): List<DocumentTag> = repository.getAllTagPairingsSync()

    suspend fun addTag(documentId: Long, name: String): Tag? = repository.addTag(documentId, name)

    suspend fun removeTag(documentId: Long, tagId: Long) = repository.removeTag(documentId, tagId)

    fun addImagesToDocument(documentId: Long, imageUris: List<Uri>) = viewModelScope.launch {
        repository.addImagesToDocument(documentId, imageUris)
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DocumentViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DocumentViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}