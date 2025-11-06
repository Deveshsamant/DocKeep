package com.dockeep.app.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.dockeep.app.database.AppDatabase
import com.dockeep.app.database.Person
import com.dockeep.app.database.PersonDao

class PersonRepository private constructor(private val personDao: PersonDao, private val documentRepository: DocumentRepository) {
    
    fun getAllPeople(): LiveData<List<Person>> {
        return personDao.getAllPeople()
    }
    
    fun searchPeople(query: String): LiveData<List<Person>> {
        return personDao.searchPeople("%$query%")
    }
    
    suspend fun insert(person: Person) {
        // Set the order to the max order + 1 if it's not already set
        val maxOrder = personDao.getMaxOrder() ?: 0
        val personWithOrder = person.copy(order = maxOrder + 1)
        personDao.insert(personWithOrder)
    }
    
    suspend fun update(person: Person) {
        personDao.update(person)
    }
    
    suspend fun updatePersonOrder(people: List<Person>) {
        // Create a copy of the list to avoid ConcurrentModificationException
        val peopleCopy = people.toList()
        for ((index, person) in peopleCopy.withIndex()) {
            val updatedPerson = person.copy(order = index)
            personDao.update(updatedPerson)
        }
    }
    
    suspend fun delete(person: Person, context: Context) {
        // First, get all documents associated with this person
        val documents = documentRepository.getDocumentsForPersonSync(person.id)
        
        // Delete each document (which will also delete associated images)
        for (document in documents) {
            documentRepository.deleteDocument(document)
        }
        
        // Finally, delete the person
        personDao.delete(person)
    }
    
    fun getPersonById(id: Long): LiveData<Person?> {
        return personDao.getPersonById(id)
    }
    
    suspend fun getAllPeopleSync(): List<Person> {
        return personDao.getAllPeopleSync()
    }
    
    suspend fun getPersonByNameSync(name: String): Person? {
        return personDao.getPersonByNameSync(name)
    }
    
    companion object {
        @Volatile
        private var INSTANCE: PersonRepository? = null
        
        fun getInstance(database: AppDatabase, context: Context): PersonRepository {
            return INSTANCE ?: synchronized(this) {
                val documentRepository = DocumentRepository.getInstance(database, context)
                val instance = PersonRepository(database.personDao(), documentRepository)
                INSTANCE = instance
                instance
            }
        }
    }
}