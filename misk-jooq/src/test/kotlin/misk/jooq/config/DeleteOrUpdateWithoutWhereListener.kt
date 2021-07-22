package misk.jooq.config

import org.jooq.ExecuteContext
import org.jooq.impl.DefaultExecuteListener

class DeleteOrUpdateWithoutWhereListener: DefaultExecuteListener() {
  override fun renderEnd(ctx: ExecuteContext?) {
    if (ctx?.sql()?.matches(Regex("^(?i:(UPDATE|DELETE)(?!.* WHERE ).*)$")) == true) {
      throw DeleteOrUpdateWithoutWhereException()
    }
  }
}

class DeleteOrUpdateWithoutWhereException : RuntimeException() {

}
