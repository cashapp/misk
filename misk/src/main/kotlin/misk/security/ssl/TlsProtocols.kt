package misk.security.ssl

object TlsProtocols {
  /** List of safe to use TLS protocols, in preferred order */
  val compatible = arrayOf("TLSv1.3", "TLSv1.2", "TLSv1.1")
  /** List of modern TLS protocols for extra security, in preferred order */
  val restricted = arrayOf("TLSv1.3", "TLSv1.2")
}
