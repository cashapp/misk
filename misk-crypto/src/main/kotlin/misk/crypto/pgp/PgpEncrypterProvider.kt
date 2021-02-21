package misk.crypto.pgp

import com.google.inject.Provider
import misk.crypto.KeyAlias
import misk.crypto.KeyReader
import misk.crypto.PgpEncrypterManager
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import java.io.BufferedInputStream
import javax.inject.Inject

internal class PgpEncrypterProvider(
  private val alias: KeyAlias
) : Provider<PgpEncrypter>, KeyReader() {
  @Inject private lateinit var pgpEncrypterManager: PgpEncrypterManager

  override fun get(): PgpEncrypter {
    val key = getRawKey(alias)
    val keyIn = BufferedInputStream(key.encrypted_key.value.byteInputStream())
    val pgpPub =
      PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(keyIn), JcaKeyFingerprintCalculator())

    // While there may be several subkeys we want specifically the subkey for encryption.
    var encryptionKey: PGPPublicKey? = null
    pgpPub.keyRings.next().publicKeys.forEach {
      if (it.isEncryptionKey) {
        encryptionKey = it
      }
    }

    if (encryptionKey == null) {
      error("no public key suitable for encryption was found")
    }

    return RealPgpEncrypter(encryptionKey!!).also {
      pgpEncrypterManager[key.key_name] = it
    }
  }
}
