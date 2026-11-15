package wisp.security.ssl

import java.security.KeyStore

/** A set of trusted root certificates. */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "TrustStore(keyStore)", imports = ["misk.security.ssl.TrustStore"]),
)
data class TrustStore(val keyStore: KeyStore)
