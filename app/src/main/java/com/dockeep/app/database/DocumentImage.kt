package com.dockeep.app.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.Date

/**
 * One block inside a document.
 *
 * A document is an ordered sequence of blocks, not just of images: a scan can
 * be followed by a note explaining what it is, a policy number, a reminder of
 * where the original is kept. [blockType] says which kind this row is, and the
 * two payload columns are used accordingly — [imagePath] for a scan, [text]
 * for a note.
 *
 * The table is still called `document_images` because renaming it would mean
 * rebuilding it in a migration for no functional gain.
 */
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

    /** Absolute path to the scan. Empty for a text block. */
    val imagePath: String,

    val createdAt: Date = Date(),
    val order: Int = 0,

    /** [BLOCK_IMAGE] or [BLOCK_TEXT]. */
    val blockType: String = BLOCK_IMAGE,

    /** The note's body. Null for an image block. */
    val text: String? = null,

    /**
     * Text read off this scan by OCR, or null if it has not been read yet.
     *
     * Stored so search can look inside documents rather than only at their
     * names, and so a new document can suggest its own name from what the
     * first page actually says.
     */
    val ocrText: String? = null
) : Serializable {

    /** True when this row is a scan rather than a note. */
    val isImage: Boolean get() = blockType == BLOCK_IMAGE

    /** True when this row is a note rather than a scan. */
    val isText: Boolean get() = blockType == BLOCK_TEXT

    companion object {
        const val BLOCK_IMAGE = "IMAGE"
        const val BLOCK_TEXT = "TEXT"
    }
}
