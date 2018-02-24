package misk.security.ssl

import javax.net.ssl.SSLContext

object CipherSuites {
  // Set of cipher suites that are safe to allow, in preferred order
  private val allowableCipherSuites = listOf(
      "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
      "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
      "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
      "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
      "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
      "TLS_RSA_WITH_AES_128_CBC_SHA256",
      "TLS_RSA_WITH_AES_256_CBC_SHA256",
      "TLS_RSA_WITH_AES_128_CBC_SHA",
      "TLS_RSA_WITH_AES_256_CBC_SHA",
      "TLS_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_RSA_WITH_AES_256_GCM_SHA384"
  )

  // Set of available cipher suites that are safe to allow.
  val safe = {
    val availableCipherSuites = SSLContext.getDefault().supportedSSLParameters.cipherSuites.toSet()

    // We iterate through the allowable suites so that we preserve preferred ordering
    allowableCipherSuites.filter {
      availableCipherSuites.contains(it)
    }.toTypedArray()
  }()

}
