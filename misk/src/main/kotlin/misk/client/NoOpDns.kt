package misk.client

import java.net.InetAddress
import okhttp3.Dns

internal object NoOpDns : Dns {
  private val loopback = listOf(InetAddress.getLoopbackAddress())

  override fun lookup(hostname: String) = loopback
}
