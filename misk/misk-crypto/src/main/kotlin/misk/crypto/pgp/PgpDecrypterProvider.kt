package misk.crypto.pgp

import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.inject.Inject
import com.google.inject.Provider
import com.squareup.moshi.Moshi
import misk.crypto.KeyAlias
import misk.crypto.KeyReader
import misk.crypto.PgpDecrypterManager
import misk.moshi.adapter
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import java.util.Base64

internal class PgpDecrypterProvider(
  private val alias: KeyAlias,
) : Provider<PgpDecrypter>, KeyReader() {
  @Inject private lateinit var moshi: Moshi
  @Inject private lateinit var pgpDecrypterManager: PgpDecrypterManager

  override fun get(): PgpDecrypter {
    val key = getRawKey(alias)
    val jsonAdapter = moshi.adapter<PgpKeyJsonFile>()
    val pgpKeyJsonFile =
      jsonAdapter.fromJson(key.encrypted_key!!.value) ?: error("could not deserialize json file")

    val decoded = Base64.getDecoder().decode(pgpKeyJsonFile.encrypted_private_key)

    val masterKey = kmsClient.getAead(key.kms_uri)
    val kek = KmsEnvelopeAead(KEK_TEMPLATE, masterKey)
    val secretKeyStream = kek.decrypt(decoded, null).inputStream()

    val pgpSec = PGPSecretKeyRingCollection(
      PGPUtil.getDecoderStream(secretKeyStream),
      JcaKeyFingerprintCalculator()
    )

    // It's common for there to be subkeys within the keyring.
    val pbeSecretKeyDecryptor = JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(null)
    val privateKeys = mutableMapOf<Long, PGPPrivateKey>()
    pgpSec.keyRings.next().secretKeys.forEach {
      privateKeys[it.keyID] = it.extractPrivateKey(pbeSecretKeyDecryptor)
    }

    return RealPgpDecrypter(privateKeys).also {
      pgpDecrypterManager[key.key_name] = it
    }
  }
}
