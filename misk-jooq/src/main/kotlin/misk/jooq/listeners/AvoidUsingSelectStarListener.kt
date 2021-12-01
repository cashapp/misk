package misk.jooq.listeners

import org.jooq.ExecuteContext
import org.jooq.impl.DefaultExecuteListener

class AvoidUsingSelectStarListener : DefaultExecuteListener() {

  /**
   * This catches any query that has a select * from or select table.* from.
   * We don't want to use any query that uses a select * in it, as jooq has a hard time converting
   * the result set into a jooq table record. It captures the result set via indexes and not the
   * column names. If you try to fetch the result set into a jooq record, jooq will expect the order
   * in which the columns are returned in the query matches the order of in which the columns are
   * declared in the jooq generated code.
   * I suppose it does ResultSet.get(0), ResulSet.get(1) instead of doing ResultSet.get(<column name)
   *
   * If the databases in dev, staging and prod don't all have the same column ordering, then things
   * start to fail.
   *
   * Either way from a code maintainability point of view it is best to avoid `select * from` and
   * always specify the columns you need. If you need all the columns in a table 2 ways of doing that
   * in jooq
   * ```
   * ctx.selectFrom(<table name>)...
   * ```
   * If you are joining multiple tables and need the columns of only one table
   *
   * ```
   * ctx.select(<jooq gen table>.fields().toList()).from(<table>.innerJoin....)
   * ```
   *
   * DO NOT DO THIS:
   * ```
   * ctx.select(<jooq gen table>.asterisk()).from(<table>)...
   * ```
   * This listener's purpose is to catch the above and prevent it from happening.
   */
  override fun renderEnd(ctx: ExecuteContext?) {
    if (ctx?.sql()?.matches(selectStarFromRegex) == true) {
      throw AvoidUsingSelectStarException(
        "Do not use select * from. " +
          "Please read the docs of class AvoidUsingSelectStartListener#renderEnd to learn more. " +
          "Generated [sql=${ctx.sql()}"
      )
    }
  }

  companion object {
    val selectStarFromRegex = Regex(
      "select(.*?\\.\\*.*?)from.*",
      setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
  }
}

class AvoidUsingSelectStarException(message: String) : RuntimeException(message) {}
