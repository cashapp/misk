package misk.jdbc

import mu.KotlinLogging
import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import net.ttddyy.dsproxy.listener.MethodExecutionContext
import net.ttddyy.dsproxy.listener.MethodExecutionListener
import net.ttddyy.dsproxy.listener.QueryExecutionListener
import java.util.Locale

@Deprecated("Replace the dependency on misk-jdcb-testing with testFixtures(misk-jdbc)")
open class ExtendedQueryExecutionListener : QueryExecutionListener, MethodExecutionListener {
  override fun beforeMethod(executionContext: MethodExecutionContext) {
    if (isStartTransaction(executionContext)) {
      beforeStartTransaction()
    }
    if (isRollbackTransaction(executionContext)) {
      doBeforeRollback()
    }
    if (isCommitTransaction(executionContext)) {
      doBeforeCommit()
    }
  }

  override fun afterMethod(executionContext: MethodExecutionContext) {
    if (isStartTransaction(executionContext)) {
      afterStartTransaction()
    }
    if (isRollbackTransaction(executionContext)) {
      doAfterRollback()
    }
    if (isCommitTransaction(executionContext)) {
      doAfterCommit()
    }
  }

  private fun isStartTransaction(executionContext: MethodExecutionContext) =
    executionContext.method.name == "setAutoCommit" && executionContext.methodArgs[0] == false

  private fun isRollbackTransaction(executionContext: MethodExecutionContext) =
    executionContext.method.name == "rollback"

  private fun isCommitTransaction(executionContext: MethodExecutionContext) =
    executionContext.method.name == "commit"

  final override fun beforeQuery(execInfo: ExecutionInfo?, queryInfoList: List<QueryInfo>?) {
    if (queryInfoList == null) return

    for (info in queryInfoList) {
      val query = info.query.toLowerCase()
      if (query == "begin") {
        beforeStartTransaction()
      } else if (query == "commit") {
        doBeforeCommit()
      } else if (query == "rollback") {
        doBeforeRollback()
      } else {
        beforeQuery(query)
      }
    }
  }

  final override fun afterQuery(execInfo: ExecutionInfo?, queryInfoList: List<QueryInfo>?) {
    if (queryInfoList == null) return

    for (info in queryInfoList) {
      val query = info.query.toLowerCase(Locale.ROOT)
      if (query == "begin") {
        afterStartTransaction()
      } else if (query == "commit") {
        doAfterCommit()
      } else if (query == "rollback") {
        doAfterRollback()
      } else {
        afterQuery(query)
      }
    }
  }

  private fun doBeforeCommit() {
    beforeEndTransaction()
    beforeCommitTransaction()
  }

  private fun doBeforeRollback() {
    try {
      beforeEndTransaction()
      beforeRollbackTransaction()
    } catch (e: Exception) {
      logger.error("Exception in before callback for rollback, " +
        "logging error instead of propagating so rollback can proceed", e)
    }
  }

  private fun doAfterCommit() {
    afterEndTransaction()
    afterCommitTransaction()
  }

  private fun doAfterRollback() {
    afterEndTransaction()
    afterRollbackTransaction()
  }

  protected open fun beforeRollbackTransaction() {}
  protected open fun beforeCommitTransaction() {}
  protected open fun beforeEndTransaction() {}
  protected open fun beforeStartTransaction() {}
  protected open fun beforeQuery(query: String) {}
  protected open fun afterRollbackTransaction() {}
  protected open fun afterCommitTransaction() {}
  protected open fun afterEndTransaction() {}
  protected open fun afterStartTransaction() {}
  protected open fun afterQuery(query: String) {}

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
