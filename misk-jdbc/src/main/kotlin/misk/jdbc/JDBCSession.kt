package misk.jdbc

import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class JDBCSession(val connection: Connection): Session {
  private val hooks: ConcurrentMap<HookType, List<() -> Unit>> = ConcurrentHashMap()

  override fun <T> useConnection(work: (Connection) -> T): T {
    return work(connection)
  }

  override fun onPostCommit(work: () -> Unit) {
    hooks.add(HookType.POST, work)
  }

  override fun onPreCommit(work: () -> Unit) {
    hooks.add(HookType.PRE, work)
  }

  override fun onSessionClose(work: () -> Unit) {
    hooks.add(HookType.SESSION_CLOSE, work)
  }

  fun executePreCommitHooks() {
    hooks[HookType.PRE]?.forEach { it() }
  }

  fun executePostCommitHooks() {
    hooks[HookType.POST]?.forEach {
      try {
        it()
      } catch (e: Exception) {
        throw PostCommitHookFailedException(e)
      }
    }
  }

  fun executeSessionCloseHooks() {
    hooks[HookType.SESSION_CLOSE]?.forEach { it() }
  }

  fun ConcurrentMap<HookType, List<() -> Unit>>.add(hookType: HookType, work: () -> Unit) {
    merge(hookType, listOf(work)) { oldList, newList ->
      mutableListOf<() -> Unit>().apply {
        addAll(oldList)
        addAll(newList)
      }.toList()
    }
  }

  enum class HookType{
    PRE,
    POST,
    SESSION_CLOSE
  }

  /**
   * Allows for destructuring the JooqSession and writing simpler code like this
   * transacter.transaction { (connection) -> ... }
   */
  operator fun component1(): Connection = connection
}

