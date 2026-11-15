package misk.hibernate.vitess

import org.hibernate.dialect.hint.QueryHintHandler

/**
 * VitessQueryHintHandler is responsible for adding Vitess-specific query hints to SQL queries, using the Hibernate
 * `QueryHintHandler` interface.
 *
 * It processes the provided hints, extracts Vitess hints prefixed with "vt+", and inserts them to the SQL query in the
 * format expected by Vitess.
 */
class VitessQueryHintHandler : QueryHintHandler {
  companion object {
    fun getQueryStringWithHints(query: String, hints: String): String {
      var modifiedQuery = query

      // Extract and consolidate all Vitess hints which are prefixed with "vt+".
      val vitessCommentDirectiveBuilder = StringBuilder()
      hints
        .split(",")
        .map { it.trim() }
        .filter { it.startsWith("vt+") }
        .forEach { hint ->
          val directive = hint.substring(3).trim() // Remove "vt+"
          vitessCommentDirectiveBuilder.append(directive).append(" ")
        }

      // If there are Vitess query hints, insert them into the query.
      if (vitessCommentDirectiveBuilder.isNotEmpty()) {
        val selectIndex = modifiedQuery.lowercase().indexOf("select")
        if (selectIndex != -1) {
          val insertPosition = selectIndex + "select".length
          val vitessCommentDirectives = " /*vt+ ${"$vitessCommentDirectiveBuilder".trim()} */"
          return modifiedQuery.substring(0, insertPosition) +
            vitessCommentDirectives +
            modifiedQuery.substring(insertPosition)
        }
      }

      return modifiedQuery
    }
  }

  override fun addQueryHints(query: String, hints: String): String {
    return getQueryStringWithHints(query, hints)
  }
}
