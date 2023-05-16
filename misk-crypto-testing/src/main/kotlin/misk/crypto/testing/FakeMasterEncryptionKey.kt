package misk.crypto.testing

import com.google.crypto.tink.Aead
import java.util.Base64

@Deprecated("Replace your dependency on misk-crypto-testing with `testImplementation(testFixtures(Dependencies.miskCrypto))`")
internal class FakeMasterEncryptionKey : Aead {

  override fun encrypt(plaintext: ByteArray?, associatedData: ByteArray?): ByteArray {
    return Base64.getEncoder().encode(plaintext)
  }

  override fun decrypt(ciphertext: ByteArray?, associatedData: ByteArray?): ByteArray {
    return Base64.getDecoder().decode(ciphertext)
  }
}
