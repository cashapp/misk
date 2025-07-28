package wisp.client

import okhttp3.Dns
import java.net.InetAddress

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(
    expression = "NoOpDns",
    imports = ["misk.client.NoOpDns"]
  )
)
object NoOpDns : Dns {
    private val loopback = listOf(InetAddress.getLoopbackAddress())

    override fun lookup(hostname: String) = loopback
}
