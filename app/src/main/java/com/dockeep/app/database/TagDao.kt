package com.dockeep.app.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun getAllTags(): LiveData<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllTagsSync(): List<Tag>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getTagByName(name: String): Tag?

    /** Ignores a duplicate name; the caller then looks the existing one up. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Update
    suspend fun updateTag(tag: Tag)

    @Delete
    suspend fun deleteTag(tag: Tag)

    // ── Pairings ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToDocument(link: DocumentTag)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId AND tagId = :tagId")
    suspend fun removeTagFromDocument(documentId: Long, tagId: Long)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId")
    suspend fun clearTagsForDocument(documentId: Long)

    @Query(
        "SELECT t.* FROM tags t " +
            "INNER JOIN document_tags dt ON dt.tagId = t.id " +
            "WHERE dt.documentId = :documentId " +
            "ORDER BY t.name COLLATE NOCASE ASC"
    )
    suspend fun getTagsForDocumentSync(documentId: Long): List<Tag>

    @Query(
        "SELECT t.* FROM tags t " +
            "INNER JOIN document_tags dt ON dt.tagId = t.id " +
            "WHERE dt.documentId = :documentId " +
            "ORDER BY t.name COLLATE NOCASE ASC"
    )
    fun getTagsForDocument(documentId: Long): LiveData<List<Tag>>

    /** Every pairing at once, for painting tag chips across a whole grid. */
    @Query("SELECT * FROM document_tags")
    suspend fun getAllPairingsSync(): List<DocumentTag>

    @Query(
        "SELECT d.* FROM documents d " +
            "INNER JOIN document_tags dt ON dt.documentId = d.id " +
            "WHERE dt.tagId = :tagId " +
            "ORDER BY d.`order` ASC, d.updatedAt DESC"
    )
    fun getDocumentsWithTag(tagId: Long): LiveData<List<Document>>

    /** Tags that no document carries any more, for tidying the tag list. */
    @Query(
        "DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM document_tags)"
    )
    suspend fun deleteUnusedTags()
}
