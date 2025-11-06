package com.dockeep.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.dockeep.app.database.AppDatabase
import com.dockeep.app.database.Person
import com.dockeep.app.repository.PersonRepository
import kotlinx.coroutines.launch

class PersonViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PersonRepository = PersonRepository.getInstance(
        AppDatabase.getDatabase(application),
        application
    )
    
    fun getAllPeople(): LiveData<List<Person>> {
        return repository.getAllPeople()
    }
    
    fun searchPeople(query: String): LiveData<List<Person>> {
        return repository.searchPeople(query)
    }
    
    fun insertPerson(person: Person) {
        viewModelScope.launch {
            repository.insert(person)
        }
    }
    
    fun updatePerson(person: Person) {
        viewModelScope.launch {
            repository.update(person)
        }
    }
    
    fun updatePersonOrder(people: List<Person>) {
        viewModelScope.launch {
            repository.updatePersonOrder(people)
        }
    }
    
    fun deletePerson(person: Person) {
        viewModelScope.launch {
            repository.delete(person, getApplication())
        }
    }
    
    fun getPersonById(id: Long): LiveData<Person?> {
        return repository.getPersonById(id)
    }
    
    suspend fun getAllPeopleSync(): List<Person> {
        return repository.getAllPeopleSync()
    }
    
    suspend fun getPersonByNameSync(name: String): Person? {
        return repository.getPersonByNameSync(name)
    }
    
    class Factory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PersonViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}