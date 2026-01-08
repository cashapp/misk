package misk.security.cert

import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException
import java.security.cert.Certificate
import java.security.cert.X509Certificate

fun X509Certificate.isSignedBy(cert: Certificate) = isSignedBy(cert.publicKey)

fun X509Certificate.isSignedBy(key: PublicKey): Boolean =
  try {
    verify(key)
    true
  } catch (sigEx: SignatureException) {
    false
  } catch (keyEx: InvalidKeyException) {
    false
  }

val X509Certificate.isSelfSigned: Boolean
  get() = isSignedBy(this)
