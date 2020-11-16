package misk.jdbc

import com.google.common.collect.ImmutableSet
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.math.BigInteger
import java.sql.ResultSet

/**
 * A MySQL query explanation.
 */
internal class Explanation {
  internal var id: BigInteger? = null
  internal var select_type: String? = null

  /**
   * The name of the table to which the row of output refers.
   */
  internal var table: String? = null

  /**
   * The partitions from which records would be matched by the query. The value is NULL for
   * nonpartitioned tables.
   */
  internal var partitions: String? = null

  /**
   * The join type. For descriptions of the different types, see
   * https://dev.mysql.com/doc/refman/5.7/en/explain-output.html#explain-join-types.
   */
  internal var type: String? = null

  /**
   * The possible_keys column indicates which indexes MySQL can choose from to find the rows in
   * this table. Note that this column is totally independent of the order of the tables as
   * displayed in the output from EXPLAIN. That means that some of the keys in possible_keys might
   * not be usable in practice with the generated table order.
   *
   * If this column is NULL (or undefined in JSON-formatted output), there are no relevant
   * indexes. In this case, you may be able to improve the performance of your query by examining
   * the WHERE clause to check whether it refers to some column or columns that would be suitable
   * for indexing.
   */
  internal var possible_keys: String? = null

  /**
   * The key column indicates the key (index) that MySQL actually decided to use. If MySQL decides
   * to use one of the possible_keys indexes to look up rows, that index is listed as the key
   * value.
   *
   * It is possible that key will name an index that is not present in the possible_keys value.
   * This can happen if none of the possible_keys indexes are suitable for looking up rows, but
   * all the columns selected by the query are columns of some other index. That is, the named
   * index covers the selected columns, so although it is not used to determine which rows to
   * retrieve, an index scan is more efficient than a data row scan.
   *
   * For InnoDB, a secondary index might cover the selected columns even if the query also selects
   * the primary key because InnoDB stores the primary key value with each secondary index. If key
   * is NULL, MySQL found no index to use for executing the query more efficiently.
   */
  internal var key: String? = null

  /**
   * The key_len column indicates the length of the key that MySQL decided to use. The value of
   * key_len enables you to determine how many parts of a multiple-part key MySQL actually uses.
   * If the key column says NULL, the ken_len column also says NULL.
   *
   * Due to the key storage format, the key length is one greater for a column that can be NULL
   * than for a NOT NULL column.
   */
  internal var key_len: String? = null

  /**
   * The ref column shows which columns or constants are compared to the index named in the key
   * column to select rows from the table.
   *
   * If the value is func, the value used is the result of some function. To see which function,
   * use SHOW WARNINGS following EXPLAIN to see the extended EXPLAIN output. The function might
   * actually be an operator such as an arithmetic operator.
   */
  internal var ref: String? = null

  /**
   * The rows column indicates the number of rows MySQL believes it must examine to execute the
   * query.
   *
   * For InnoDB tables, this number is an estimate, and may not always be exact.
   */
  internal var rows: BigInteger? = null

  /**
   * The filtered column indicates an estimated percentage of table rows that will be filtered by
   * the table condition. That is, rows shows the estimated number of rows examined and
   * rows × filtered / 100 shows the number of rows that will be joined with previous tables.
   */
  internal var filtered: Double? = null

  /**
   * This column contains additional information about how MySQL resolves the query. For
   * descriptions of the different values, see
   * https://dev.mysql.com/doc/refman/5.7/en/explain-output.html#explain-extra-information.
   */
  internal var Extra: String? = null

  // MySQL chose a key, let's make sure it's in possible_keys. If not, the key isn't likely
  // going to be helpful.
  // Sometimes MySQL knows the answer without having to do any work.
  fun isIndexed(): Boolean {
    if (key != null && possible_keys != null) {
      if (possible_keys!!.contains(key!!)) {
        return true
      }
    }
    return Extra != null && NO_INDEX_NEEDED_MESSAGES.contains(Extra!!)
  }

  /**
   * If the query has possible keys, takes input joined from another subquery, or uses the
   * PRIMARY key to fetch a limited number of rows, allow with a warning.
   */
  internal fun isProbablyOkay(query: String): Boolean {
    if (possible_keys != null && !possible_keys!!.isEmpty()) {
      return true
    }

    if (table != null) {
      if (table!!.startsWith("<union")) {
        // {code}<unionM,N>{code}: The row refers to the union of the rows with id values of M
        // and N.
        return true
      }
      if (table!!.startsWith("<derived")) {
        // {code}<derivedN>{code}: The row refers to the derived table result for the row with an
        // id value of N. A derived table may result, for example, from a subquery in the FROM
        // clause.
        return true
      }
      if (table!!.startsWith("<subquery")) {
        // {code}<subqueryN>{code}: The row refers to the result of a materialized subquery for
        // the row with an id value of N.
        // See https://dev.mysql.com/doc/refman/5.7/en/subquery-materialization.html.
        return true
      }
    }

    return "PRIMARY" == key && query.contains(" limit ")
  }

  override fun toString(): String = buildString {
    for (field in fields) {
      val value = field.get(this@Explanation) ?: continue
      append(", ")
      append(field.name)
      append("=")
      append(value)
    }
    replace(0, 2, "Explanation{")
    append('}')
  }

  companion object {
    /**
     * Some explanations don't have index information but it's not a problem.  For example, in
     * tests when tables are empty and you call max(id), it'll say "No matching min/max row".
     *
     * See EXPLAIN Extra Information at https://dev.mysql.com/doc/refman/5.7/en/explain-output.html.
     */
    internal val NO_INDEX_NEEDED_MESSAGES = ImmutableSet.of(
      "const row not found",
      "Impossible HAVING",
      "Impossible WHERE",
      "Impossible WHERE noticed after reading const tables",
      "No matching min/max row",
      "no matching row in const table",
      "No matching rows after partition pruning",
      "No tables used",
      "Select tables optimized away",
      "unique row not found",
      "Using where; Open_frm_only; Scanned 0 databases"
    )

    fun fromResultSet(rs: ResultSet): Explanation {
      val result = Explanation()
      for (f in fields) {
        f.set(result, rs.getObject(f.name))
      }
      return result
    }

    private val fields: List<Field> =
      Explanation::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.map {
        it.isAccessible = true
        it
      }
  }
}
