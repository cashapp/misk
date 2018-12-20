package misk.client

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/**
 * EnvoyHttpEgressSocketFactory ensures that any socket that's created to go to a specific
 * destination actually ends up creating a socket that connects to the local Envoy sidecar so
 * that it can handle egress to the appropriate service.
 */
class EnvoyHttpEgressSocketFactory constructor(private val port: Int) : SocketFactory() {
  override fun createSocket(): Socket {
    return createEnvoyHttpSocket()
  }

  override fun createSocket(host: String?, port: Int): Socket {
    return createEnvoyHttpSocket()
  }

  override fun createSocket(host: String?, port: Int, localhost: InetAddress?, localPort: Int): Socket {
    return createEnvoyHttpSocket()
  }

  override fun createSocket(host: InetAddress?, port: Int): Socket {
    return createEnvoyHttpSocket()
  }

  override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
    return createEnvoyHttpSocket()
  }

  private fun createEnvoyHttpSocket(): Socket {
    return object : Socket() {
      var inetSocketAddress: InetSocketAddress? = null

      override fun connect(endpoint: SocketAddress?) {
        connect(endpoint, 0)
      }

      override fun connect(endpoint: SocketAddress?, timeout: Int) {
        inetSocketAddress = endpoint as? InetSocketAddress
        super.connect(InetSocketAddress("localhost", this@EnvoyHttpEgressSocketFactory.port), timeout)
      }

      override fun getInetAddress(): InetAddress {
        return inetSocketAddress!!.address
      }

    }
  }
}