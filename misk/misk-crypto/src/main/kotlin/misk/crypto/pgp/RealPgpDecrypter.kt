package misk.crypto.pgp

import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPOnePassSignatureList
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder
import org.bouncycastle.util.io.Streams

/**
 * RealPgpDecrypter is a simple PGP decryption helper.
 * The implementation makes a  couple key assumptions:
 * - The input data is public key encrypted.
 * - The input data contains only ONE encrypted payload.
 * - The first OR second packet in the input must be the encryption method packet.
 * - No signature verification.
 */
internal class RealPgpDecrypter(
  private val privateKeys: Map<Long, PGPPrivateKey>
) : PgpDecrypter {
  override fun decrypt(ciphertext: ByteArray): ByteArray {
    val buffer = okio.Buffer()
    val outputStream = buffer.outputStream()
    val decoderStream = PGPUtil.getDecoderStream(ciphertext.inputStream())
    val jcaPGPObjectFactory = JcaPGPObjectFactory(decoderStream)

    val pgpEncryptedDataList = when (val nextObject = jcaPGPObjectFactory.nextObject()) {
      is PGPEncryptedDataList -> nextObject
      else -> jcaPGPObjectFactory.nextObject() as PGPEncryptedDataList
    }

    // Assuming that the first element in the EncryptedDataList is public key encrypted.
    val pgpPublicKeyEncryptedData =
      pgpEncryptedDataList.encryptedDataObjects.next() as PGPPublicKeyEncryptedData

    val pgpSecretKey = privateKeys[pgpPublicKeyEncryptedData.keyID]
      ?: error("no private key able to decrypt this message")

    val publicKeyDataDecryptorFactory = JcePublicKeyDataDecryptorFactoryBuilder()
      .setProvider("BC")
      .setContentProvider("BC")
      .build(pgpSecretKey)

    val clear = pgpPublicKeyEncryptedData.getDataStream(publicKeyDataDecryptorFactory)
    val plainFactory = JcaPGPObjectFactory(clear)
    var message = plainFactory.nextObject()
    if (message is PGPCompressedData) {
      val pgpFactory = JcaPGPObjectFactory(message.dataStream)
      message = pgpFactory.nextObject()
    }

    when (message) {
      is PGPLiteralData -> {
        Streams.pipeAll(message.inputStream, outputStream)
        outputStream.close()
      }

      is PGPOnePassSignatureList ->
        throw PGPException("encrypted message contains a signed message - not literal data.")

      else -> throw PGPException("message is not a simple encrypted file - type unknown.")
    }
    if (pgpPublicKeyEncryptedData.isIntegrityProtected && !pgpPublicKeyEncryptedData.verify()) {
      error("message failed integrity check")
    }

    return buffer.readByteArray()
  }
}
