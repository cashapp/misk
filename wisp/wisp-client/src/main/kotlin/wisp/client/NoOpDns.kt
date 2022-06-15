package wisp.client

import okhttp3.Dns
import java.net.InetAddress

object NoOpDns : Dns {
    private val loopback = listOf(InetAddress.getLoopbackAddress())

    override fun lookup(hostname: String) = loopback
}
