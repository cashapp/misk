package misk.jdbc

import java.io.PrintWriter
import java.sql.Connection
import java.sql.ConnectionBuilder
import java.sql.ShardingKeyBuilder
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * Wraps a DataSource to allow it to be lazily initialized.
 * This is useful for wiring objects that depend on a DataSource in Guice modules.
 * The DataSource is lazily initialized to avoid creating a connection pool before the service is started.
 * Note that using the DataSource before the service is started will throw an exception.
 */
internal class DataSourceWrapper(private val simpleName : String?): DataSource {
  private var delegate: AtomicReference<DataSource?> = AtomicReference(null)

  fun initialize(delegate: DataSource) {
    check(this.delegate.get() == null) { "@${simpleName} DataSource already created" }
    this.delegate.set(delegate)
  }

  private fun verifyInitialized() {
    checkNotNull(delegate.get()) { "@${simpleName} DataSource not created: did you forget to start the service?" }
  }
  override fun getLogWriter(): PrintWriter {
    verifyInitialized()
    return delegate.get()!!.getLogWriter()
  }

  override fun setLogWriter(out: PrintWriter?) {
    verifyInitialized()
    return delegate.get()!!.setLogWriter(out)
  }

  override fun setLoginTimeout(seconds: Int) {
    verifyInitialized()
    return delegate.get()!!.setLoginTimeout(seconds)
  }

  override fun getLoginTimeout(): Int {
    verifyInitialized()
    return delegate.get()!!.getLoginTimeout()
  }

  override fun getParentLogger(): Logger {
    verifyInitialized()
    return delegate.get()!!.getParentLogger()
  }

  override fun <T : Any?> unwrap(iface: Class<T>?): T {
    verifyInitialized()
    return delegate.get()!!.unwrap(iface)
  }

  override fun isWrapperFor(iface: Class<*>?): Boolean {
    verifyInitialized()
    return delegate.get()!!.isWrapperFor(iface)
  }

  override fun getConnection(): Connection {
    verifyInitialized()
    return delegate.get()!!.getConnection()
  }

  override fun getConnection(username: String?, password: String?): Connection {
    verifyInitialized()
    return delegate.get()!!.getConnection(username, password)
  }

  override fun createShardingKeyBuilder(): ShardingKeyBuilder {
    verifyInitialized()
    return delegate.get()!!.createShardingKeyBuilder()
  }

  override fun createConnectionBuilder(): ConnectionBuilder {
    verifyInitialized()
    return delegate.get()!!.createConnectionBuilder()
  }
}
