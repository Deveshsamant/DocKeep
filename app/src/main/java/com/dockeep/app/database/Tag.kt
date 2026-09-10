package com.dockeep.app.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.Date

/**
 * A label that cuts across people.
 *
 * People answer "whose is this"; tags answer "what is this about" — TAX 2024,
 * VEHICLE, MEDICAL — and a document can carry several. Kept as its own table
 * with a join rather than a comma-joined column so a rename touches one row
 * and filtering stays an index lookup.
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Date = Date()
) : Serializable

/**
 * Join row binding a document to a tag.
 *
 * Both sides cascade: deleting either the document or the tag clears the
 * pairing without leaving an orphan behind.
 */
@Entity(
    tableName = "document_tags",
    primaryKeys = ["documentId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId"), Index("tagId")]
)
data class DocumentTag(
    val documentId: Long,
    val tagId: Long
) : Serializable
