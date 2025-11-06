package com.dockeep.app.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY `order` ASC, name ASC")
    fun getAllPeople(): LiveData<List<Person>>

    @Query("SELECT * FROM people ORDER BY `order` ASC, name ASC")
    suspend fun getAllPeopleSync(): List<Person>

    @Query("SELECT * FROM people WHERE name LIKE :searchQuery ORDER BY `order` ASC, name ASC")
    fun searchPeople(searchQuery: String): LiveData<List<Person>>

    @Insert
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    @Delete
    suspend fun delete(person: Person)

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getPersonByIdSync(id: Long): Person?

    @Query("SELECT * FROM people WHERE id = :id")
    fun getPersonById(id: Long): LiveData<Person?>

    @Query("SELECT * FROM people WHERE name = :name LIMIT 1")
    suspend fun getPersonByNameSync(name: String): Person?
    
    @Query("SELECT MAX(`order`) FROM people")
    suspend fun getMaxOrder(): Int?
}