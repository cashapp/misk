package misk.crypto.testing

import com.google.crypto.tink.Aead
import java.util.Base64

internal class FakeMasterEncryptionKey : Aead {
  override fun encrypt(plaintext: ByteArray?, associatedData: ByteArray?): ByteArray {
    return Base64.getEncoder().encode(plaintext)
  }

  override fun decrypt(ciphertext: ByteArray?, associatedData: ByteArray?): ByteArray {
    return Base64.getDecoder().decode(ciphertext)
  }
}
