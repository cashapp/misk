package wisp.security.ssl

import java.security.KeyStore

/** A certificate and its private key. */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(
    expression = "CertStore(keyStore)",
    imports = ["misk.security.ssl.CertStore"]
  )
)
data class CertStore(val keyStore: KeyStore)
