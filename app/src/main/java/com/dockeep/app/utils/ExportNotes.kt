package com.dockeep.app.utils

/**
 * How a text block is named inside an export archive.
 *
 * The archive is a plain folder of files — that is what makes it useful to a
 * user who opens the zip on a computer — so a note has nowhere to keep its
 * identity or its position except its own filename. Both ends of the round
 * trip read that name through here, so the writer and the reader cannot drift
 * apart.
 *
 * Text blocks were previously left out of the archive entirely: a vault
 * exported as a backup and imported again came back with every note silently
 * gone.
 */
object ExportNotes {

    private const val PREFIX = "dockeep-note-"
    private const val SUFFIX = ".txt"

    /**
     * The filename for a note at [order].
     *
     * [seq] only distinguishes two notes that claim the same order, which the
     * database does not forbid.
     */
    fun fileNameFor(order: Int, seq: Int): String =
        PREFIX + "%03d".format(order) + "-" + seq + SUFFIX

    /**
     * The order encoded in [fileName], or null when it is not an exported
     * note — an ordinary scan, or a .txt the user happened to put in the zip
     * themselves.
     */
    fun orderOf(fileName: String): Int? {
        val name = fileName.substringAfterLast('/').substringAfterLast('\\')
        if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) return null
        return name.removePrefix(PREFIX).substringBefore('-').toIntOrNull()
    }

    /** True when [fileName] is a note this app exported. */
    fun isNote(fileName: String): Boolean = orderOf(fileName) != null
}
