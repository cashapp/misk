package misk.jdbc

import java.io.PrintWriter
import java.sql.Connection
import java.sql.ConnectionBuilder
import java.sql.ShardingKeyBuilder
import java.util.logging.Logger
import javax.sql.DataSource

internal class DataSourceWrapper(private val simpleName : String?): DataSource {
  private var delegate: DataSource? = null

  fun initialize(delegate: DataSource) {
    check(this.delegate == null) { "@${simpleName} DataSource already created" }
    this.delegate = delegate
  }

  private fun verifyInitialized() {
    checkNotNull(delegate) { "@${simpleName} DataSource not created: did you forget to start the service?" }
  }
  override fun getLogWriter(): PrintWriter {
    verifyInitialized()
    return delegate!!.getLogWriter()
  }

  override fun setLogWriter(out: PrintWriter?) {
    verifyInitialized()
    return delegate!!.setLogWriter(out)
  }

  override fun setLoginTimeout(seconds: Int) {
    verifyInitialized()
    return delegate!!.setLoginTimeout(seconds)
  }

  override fun getLoginTimeout(): Int {
    verifyInitialized()
    return delegate!!.getLoginTimeout()
  }

  override fun getParentLogger(): Logger {
    verifyInitialized()
    return delegate!!.getParentLogger()
  }

  override fun <T : Any?> unwrap(iface: Class<T>?): T {
    verifyInitialized()
    return delegate!!.unwrap(iface)
  }

  override fun isWrapperFor(iface: Class<*>?): Boolean {
    verifyInitialized()
    return delegate!!.isWrapperFor(iface)
  }

  override fun getConnection(): Connection {
    verifyInitialized()
    return delegate!!.getConnection()
  }

  override fun getConnection(username: String?, password: String?): Connection {
    verifyInitialized()
    return delegate!!.getConnection(username, password)
  }

  override fun createShardingKeyBuilder(): ShardingKeyBuilder {
    verifyInitialized()
    return delegate!!.createShardingKeyBuilder()
  }

  override fun createConnectionBuilder(): ConnectionBuilder {
    verifyInitialized()
    return delegate!!.createConnectionBuilder()
  }
}
