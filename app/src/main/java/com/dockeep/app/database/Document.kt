package com.dockeep.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.Date

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val colorIndex: Int = 0,
    val thumbnailPath: String? = null,
    val order: Int = 0,
    val personId: Long? = null  // Nullable to allow documents for the main user
) : Serializable