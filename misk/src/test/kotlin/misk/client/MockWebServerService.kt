package misk.client

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Singleton
import java.io.File
import okhttp3.mockwebserver.MockWebServer

@Singleton
class MockWebServerService(val unixSocketFile: String?) : AbstractIdleService() {
  var server: MockWebServer? = null
  private var file: File = File(unixSocketFile)

  override fun startUp() {
    // Cleanup from previous test runs just in case previous run did not successfully shutdown
    file.delete()

    server = MockWebServer()
    unixSocketFile?.let { server!!.serverSocketFactory = UnixDomainServerSocketFactory(file) }
  }

  override fun shutDown() {
    server?.shutdown()
    file.delete()
  }
}
