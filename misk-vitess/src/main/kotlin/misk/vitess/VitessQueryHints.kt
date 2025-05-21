package misk.vitess

class VitessQueryHints {
  companion object {

    /**
     * Return a hint to Vitess to enable scatter queries. This will only
     * work if `no_scatter` is set at the vtgate, otherwise it will function
     * as a no-op.
     */
    fun allowScatter(): String =  "vt+ ALLOW_SCATTER"

    /**
     * Return a hint to Vitess to enable best-effort scatter queries. This means
     * some shards may succeed while others fail, as opposed to the normal behavior
     * where a single shard failure causes the entire query to fail.
     */
    fun bestEffortScatter(): String = "vt+ SCATTER_ERRORS_AS_WARNINGS"

    /**
     * Return a hint to Vitess to enforce a query timeout. <b>Important</b>: This is <b>dangerous</b> if
     * you set a timeout greater than the default Vitess query timeout (queryserver-config-query-timeout),
     * and ideally this should only be used to set lower query timeouts, so <b>use with extreme caution</b>.
     */
    fun queryTimeoutMs(ms: Long): String = "vt+ QUERY_TIMEOUT_MS=$ms"
  }
}
