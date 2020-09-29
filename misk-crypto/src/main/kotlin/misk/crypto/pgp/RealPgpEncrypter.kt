package misk.crypto.pgp

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPCompressedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedData
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Date

internal class RealPgpEncrypter(
  private val encryptionKey: PGPPublicKey
) : PgpEncrypter {
  override fun encrypt(plaintext: ByteArray, armored: Boolean): ByteArray {
    val buffer = okio.Buffer()

    val byteArrayOutputStream = ByteArrayOutputStream()
    val pgpCompressedDataGenerator = PGPCompressedDataGenerator(PGPCompressedData.ZIP)
    val pgpLiteralDataGenerator = PGPLiteralDataGenerator()
    val pgpLiteralDataStream = pgpLiteralDataGenerator.open(
        pgpCompressedDataGenerator.open(byteArrayOutputStream),
        PGPLiteralData.BINARY,
        "", // Name of the file encoded in the data. Just boring metadata.
        plaintext.size.toLong(),
        Date()
    )

    pgpLiteralDataStream.write(plaintext)
    pgpLiteralDataStream.close()
    pgpCompressedDataGenerator.close()

    val outputStream = buffer.outputStream()
    val out = when (armored) {
      true -> ArmoredOutputStream(outputStream)
      else -> outputStream
    }

    val jcePGPDataEncryptorBuilder = JcePGPDataEncryptorBuilder(PGPEncryptedData.CAST5)
        .setWithIntegrityPacket(true)
        .setSecureRandom(SecureRandom())
        .setProvider("BC")

    val pgpEncryptedDataGenerator = PGPEncryptedDataGenerator(jcePGPDataEncryptorBuilder)
    val keyEncryptionMethodGenerator = JcePublicKeyKeyEncryptionMethodGenerator(encryptionKey)
        .setProvider(BouncyCastleProvider())
        .setSecureRandom(SecureRandom())
    pgpEncryptedDataGenerator.addMethod(keyEncryptionMethodGenerator)

    val bytes = byteArrayOutputStream.toByteArray()
    val encryptedOut = pgpEncryptedDataGenerator.open(out, bytes.size.toLong())
    encryptedOut.write(bytes)
    encryptedOut.close()

    if (armored) {
      out.close()
    }

    return buffer.readByteArray()
  }
}