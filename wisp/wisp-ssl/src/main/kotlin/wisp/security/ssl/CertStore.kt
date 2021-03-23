package wisp.security.ssl

import java.security.KeyStore

/** A certificate and its private key. */
data class CertStore(val keyStore: KeyStore)
