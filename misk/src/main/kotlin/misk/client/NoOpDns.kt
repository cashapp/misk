package misk.client

import java.net.InetAddress
import java.util.Collections
import javax.annotation.Nonnull
import okhttp3.Dns

class NoOpDns : Dns {
  override fun lookup(@Nonnull hostname: String): List<InetAddress> {
    return loopback
  }

  companion object {
    private val loopback = Collections.singletonList(InetAddress.getLoopbackAddress())
  }
}