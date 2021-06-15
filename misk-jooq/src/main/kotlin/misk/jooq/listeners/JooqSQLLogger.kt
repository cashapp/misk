package misk.jooq.listeners

import org.jooq.Configuration
import org.jooq.ExecuteContext
import org.jooq.ExecuteType.BATCH
import org.jooq.Param
import org.jooq.TXTFormat
import org.jooq.VisitContext
import org.jooq.VisitListenerProvider
import org.jooq.impl.DSL
import org.jooq.impl.DefaultExecuteListener
import org.jooq.impl.DefaultVisitListener
import org.jooq.impl.DefaultVisitListenerProvider
import org.jooq.tools.StringUtils
import wisp.logging.getLogger
import java.util.Arrays

class JooqSQLLogger : DefaultExecuteListener() {
  override fun renderEnd(ctx: ExecuteContext) {
    var configuration = ctx.configuration()
    val newline = if (configuration.settings().isRenderFormatted == true) "\n" else ""

    configuration = abbreviateBindVariables(configuration)
    val batchSQL: Array<out String?> = ctx.batchSQL()
    if (ctx.query() != null) {

      log.info { "Executing query ${newline + ctx.sql()}" }

      val inlined = DSL.using(configuration).renderInlined(ctx.query())
      if (ctx.sql() != inlined) log.info { "-> with bind values ${newline + inlined} " }
    } else if (!StringUtils.isBlank(ctx.sql())) {
      if (ctx.type() == BATCH) log.info {
        "Executing batch query ${newline + ctx.sql()}"
      } else log.info { "Executing query ${newline + ctx.sql()}" }
    } else if (batchSQL.isNotEmpty()) {
      if (batchSQL[batchSQL.size - 1] != null) for (sql in batchSQL) log.info {
        "Executing batch query ${newline + sql}"
      }
    }
  }

  override fun recordEnd(ctx: ExecuteContext) {
  }

  override fun resultEnd(ctx: ExecuteContext) {
    if (ctx.result() != null) {
      logMultiline(
        "Fetched result",
        ctx.result()!!
          .format(TXTFormat.DEFAULT.maxRows(5).maxColWidth(50))
      )
      log.info { "Fetched row(s) ${ctx.result()!!.size}" }
    }
  }

  override fun executeEnd(ctx: ExecuteContext) {
    if (ctx.rows() >= 0) log.info {
      "Affected row(s) ${ctx.rows()}"
    }
  }

  override fun outEnd(ctx: ExecuteContext) {
  }

  override fun exception(ctx: ExecuteContext) {
    log.info(ctx.exception()) { "Exception" }
  }

  private fun logMultiline(
    comment: String,
    message: String,
  ) {
    var commentToUse: String? = comment
    for (line in message.split("\n".toRegex()).toTypedArray()) {
      log.info { "$commentToUse $line" }
      commentToUse = ""
    }
  }

  /**
   * Add a VisitListener that transforms all bind variables by abbreviating them.
   */
  private fun abbreviateBindVariables(configuration: Configuration): Configuration {
    val oldProviders = configuration.visitListenerProviders()
    val newProviders = arrayOfNulls<VisitListenerProvider>(oldProviders.size + 1)
    System.arraycopy(oldProviders, 0, newProviders, 0, oldProviders.size)
    newProviders[newProviders.size - 1] = DefaultVisitListenerProvider(BindValueAbbreviator())
    return configuration.derive(*newProviders)
  }

  private class BindValueAbbreviator : DefaultVisitListener() {
    private var anyAbbreviations = false
    override fun visitStart(context: VisitContext) {
      if (context.renderContext() != null) {
        val part = context.queryPart()
        if (part is Param<*>) {
          val value = part.value
          if (value is String && value.length > maxLength) {
            anyAbbreviations = true
            context.queryPart(DSL.`val`(StringUtils.abbreviate(value as String?, maxLength)))
          } else if (value is ByteArray && value.size > maxLength) {
            anyAbbreviations = true
            context.queryPart(DSL.`val`(Arrays.copyOf(value, maxLength)))
          }
        }
      }
    }

    override fun visitEnd(context: VisitContext) {
      if (anyAbbreviations) {
        if (context.queryPartsLength() == 1) {
          context.renderContext()!!
            .sql(
              " -- Bind values may have been abbreviated for DEBUG logging. Use TRACE logging for very large bind variables."
            )
        }
      }
    }
  }

  companion object {
    private const val maxLength = 2000
    val log = getLogger<JooqSQLLogger>()
  }
}
