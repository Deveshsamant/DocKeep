package com.dockeep.app.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.Date

@Entity(
    tableName = "document_images",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class DocumentImage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val imagePath: String,
    val createdAt: Date = Date(),
    val order: Int = 0
) : Serializable