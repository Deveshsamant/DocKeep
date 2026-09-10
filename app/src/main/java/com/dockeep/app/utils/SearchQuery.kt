package com.dockeep.app.utils

/**
 * Turns what the user typed into a LIKE pattern.
 *
 * Lives on its own rather than inside the repository so it can be tested
 * without a database: it is the kind of thing that fails silently, since a
 * broken escape still returns rows — just the wrong ones.
 */
object SearchQuery {

    /**
     * Escapes the characters LIKE treats as operators.
     *
     * Without this, "%" matched every document and "_" matched any single
     * character, so those keys acted as commands rather than as text the user
     * was searching for. The backslash is escaped first, so the ones added
     * afterwards are not doubled.
     *
     * Pair with `ESCAPE '\'` in the query.
     */
    fun escapeForLike(query: String): String =
        query.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    /** The full pattern for a substring match. */
    fun containsPattern(query: String): String = "%" + escapeForLike(query) + "%"
}
