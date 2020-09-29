package misk.crypto

import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.inject.Guice
import com.google.inject.Injector
import com.squareup.moshi.Moshi
import misk.config.MiskConfig.RealSecret
import misk.crypto.pgp.PgpKeyJsonFile
import misk.environment.DeploymentModule
import misk.moshi.adapter
import misk.resources.ResourceLoader
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openpgp.PGPException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import javax.inject.Inject

@MiskTest(startService = true)
class PgpKeyTest {
  @Suppress("unused")
  @MiskTestModule
  val module = CryptoTestModule()

  @Inject private lateinit var kmsClient: KmsClient
  @Inject private lateinit var moshi: Moshi
  @Inject private lateinit var resourceLoader: ResourceLoader

  @Test fun `encrypt and decrypt byte array armored`() {
    val secretMessage = "foo bar baz here's a very secret message".toByteArray()

    val injector = getInjector()
    val pgpEncrypter = injector.getInstance(PgpEncrypterManager::class.java)["pgp_encrypter"]
    val pgpDecrypter = injector.getInstance(PgpDecrypterManager::class.java)["pgp_decrypter"]

    val encryptedBytes = pgpEncrypter.encrypt(secretMessage, true)
    val decryptedBytes = pgpDecrypter.decrypt(encryptedBytes)

    assertThat(encryptedBytes).isNotEqualTo(secretMessage)
    assertThat(decryptedBytes).isEqualTo(secretMessage)
  }

  @Test fun `encrypt and byte array stream`() {
    val secretMessage = "foo bar baz here's a very secret message".toByteArray()

    val injector = getInjector()
    val pgpEncrypter = injector.getInstance(PgpEncrypterManager::class.java)["pgp_encrypter"]
    val pgpDecrypter = injector.getInstance(PgpDecrypterManager::class.java)["pgp_decrypter"]

    val encryptedBytes = pgpEncrypter.encrypt(secretMessage, false)
    val decryptedBytes = pgpDecrypter.decrypt(encryptedBytes)

    assertThat(encryptedBytes).isNotEqualTo(secretMessage)
    assertThat(decryptedBytes).isEqualTo(secretMessage)
  }

  @Test fun `basic gpg generated message`() {
    val secretMessage = "foo bar baz\n"
    // Generated via `gpg -ea -r misk-crypto-example` using the public key.
    val cypherText = """
      -----BEGIN PGP MESSAGE-----
      
      hQIMAx0PAQ5Y+arHAQ/+PcL0Owd3X1DstQ85cyN6UfkLSNVyIxBuFsw5eXFMj8KP
      pvsoxFEDM5DiVeQqlra637w7Df/d9bLL2jA/pLR4rQPMdw5PZAUJ+ZRQHiGQC7SK
      JCB3eVXLbR/6gyCFlNmXEmSp+eQYizc6z7XQrLjwzhSmptCb/geyc7ZZJaRvhcli
      skl2osdCfh1HdE7sQ/hN+40NER2Yc7zUa3qn2LAvFC+cptL39z6hxfjibAr6b+2F
      4S360SQXCrjUKsADhok9xU2uU/ktWxCUY/W8cV0KBny8Mgw6jpMKn1Wht1XvRfVH
      jlMDcMbP9PDvBs/Dv0z0yta8ij+at3Gju1EBQa61NBrEgyeoHW4xl5UNuFAUOSNT
      BjeuEdUFd6qSpimtH9LrfQoll4VsLWCimKB4v61zIEcGR4HBN9g1Kx6IQB4g8wU0
      WgQMq6CLXEQI2V4YmQUgSMWg7PDJI8+fr8hJq0nRFJ+RjWd2Pfi7BqqjFhHCHsdP
      /WdfsZ3rZxy4ZO74ZdDdwOCzlVmbjliAjp0HgD0UPik+1L6OTr3c1xUrhp9pQR3t
      0f24CQRoZNp1NuH/Ig9R0KUBrLYvbr7h1reqSxI4t/KnZ3XoXur+1GQuXmoBN5Kb
      d555su2/x3RTWtFSz77lesHuAIdtne2OiXShNIJ63x4sF8W5mThxJAuaXKyxZ13S
      NwGbpIiGGQPnhmU5pIhiNsTye8Lm9lH1gaaM0s8lRghuKeDWR+tN5NA79rQw7Rv2
      Gnclp/iVFX4=
      =qmV6
      -----END PGP MESSAGE-----
      """.trimIndent()

    val injector = getInjector()
    val pgpDecrypter = injector.getInstance(PgpDecrypterManager::class.java)["pgp_decrypter"]
    val plaintext = pgpDecrypter.decrypt(cypherText.toByteArray()).decodeToString()
    assertThat(plaintext).isEqualTo(secretMessage)
  }

  @Test fun `uncompressed gpg generated message`() {
    val secretMessage = "foo bar baz\n"
    // Generated via `gpg -ea --compress-algo none -r misk-crypto-example`
    // using the public key.
    val cypherText = """
      -----BEGIN PGP MESSAGE-----
      
      hQIMAx0PAQ5Y+arHAQ/8DsQlRHLxg1h0kCRo5UklJNj/zW1j+R16Wm0GQGEagdVl
      btXMJlFlLMm3onHeVr9Bi6P6ShBcKePRg7qipzpgbw9I7h5KLB2g28Ac2xBrqbVU
      gYTvE9lGT9ma5F88iRDdzwk/6GFX6M7PPYeJPlO/G8aI0iD6RqWsYp2YcBh51t3D
      e/OLtkkCmPTh+h7G7thvlyyoOvlWkPqtJlV6CJ3N+/quA+o6bRZJlCCX0J5TIBgO
      bnldBv+yfZ8eMC5AfthHvVfOA0P6A2Is/WGKjIll2NZbcRcMb1gOhMyJ9OBZEWy9
      lA8bwpbF2f6p/sns0/Di5fSIr+8gktZfXy+0A/ktvlVUGepUjC2LaisgrcqyVomy
      s2lz0hqA8xCqHy3zcHTEdbyRdQJaXyNfA0csT+B7aUGWJ4xzuzpxTM2UfVY+g20y
      ffPwCBBwmmm6wlA6qOgCJmXpjWKnkJPXYVJVFYaQNs5Jf7eq3cOr8NE7vFudutNu
      YwE0uhJimo7j7J5mGIjyZLjmJgKfmdX9PzCFkh5U7ZGuuempxEcEgSRtB0o++7L6
      uwS/npZ3yAlhm8roSF5K47lgOyVnRgQ/n34yvNhX3vTdRbztCPdXYUEQgMu9Uuey
      4PqQ9LuJCI61dG3ExYnaWy0VVFYGLYGOu/JuI0ivPgOUGHlCMxwWa9UxCrMEuFXS
      NQEVur1AycurFUWWRVORedovmLOMAmi9iB7hmRvknHJz7VlvuZns1AeyGMdJQk4P
      tk38rqcb
      =E7kq
      -----END PGP MESSAGE-----
      """.trimIndent()

    val injector = getInjector()
    val pgpDecrypter = injector.getInstance(PgpDecrypterManager::class.java)["pgp_decrypter"]
    val plaintext = pgpDecrypter.decrypt(cypherText.toByteArray()).decodeToString()
    assertThat(plaintext).isEqualTo(secretMessage)
  }

  @Test fun `signed gpg generated message`() {
    // TODO: Support signning for both encryption and decryption.

    // Generated via `gpg -ea --sign -r misk-crypto-example` using the
    // public and private key.
    val cypherText = """
      -----BEGIN PGP MESSAGE-----
      
      hQIMAx0PAQ5Y+arHAQ/+N8uDtjbAqAPZ6U8Rmm02ni3frpzIMhddn6phMqY9heeg
      +pCzSPYTeCRL5O432BYhvjc0vzW9U06R7fStPDrFQtS3dgGfS3l5cK5GhmGNzYIX
      ER6uGdGy7jB2X5hfm4hPxVhzEIIsqqweKsxzcay9aft5AYnhA8PTGpzKXKGDpNb+
      zJjB7XidM9TTfK+pb3X4TX0FFVLlCr1ulxlyuZnXsL+ze8mfi9ZxQ2JWSfEHttBM
      dXqsLXoCQHx1GS8TECMqLPob3EAC0kUEEHkjeQI2wqvDx8c7Cjv+wgrNIc70d9Qq
      MsYttJj4mDSbVhWHWblD/eEtW34Do89a3InD22SkDCwdBbhmEGYILnmZ0U42Zhnf
      QGrgcmBvclXk12x2EUMLdiAWVbyixKmxWFh0XpmoVDcefnu2mEC34SC+5fkPk+Vg
      MnQdpbMkYuBsOeGj785ERPZASnQM0YbvfQKN7jkTueKgLLeu2+5N6A3d3JGqkIZD
      fG9/gIFgLeDe5TGN0ynfljfs+XhSH9YaTYRXXy703u75q0SLmAFDC2I+68RXYDE9
      3dx/Gty6nBkKRsIcHR/WNAF3E5AVDJ6HotehIj+f8FvAEbgLSZtuexAcokbkX1x5
      Bdn/vJ0k94Dvee09wOw+A20c6MlEIhVNrZjkoihLWeMiHwKwwpuVKxtSaXRP9p3S
      6QFTplodeJUGl/VjZ9puDhlifu+up4qBtFNtipGdJ/YE5yWI358/Zt7S6iqRxou+
      AIUPPcBL81i1K78CvDdkQfUDWeZ0k5poUTC3IqbLMqGEjeUyW7Fpnefhs+e1Fx7j
      NqbIcz9Sy5NaqXL6R3leLKL4NX4sWaVbY/znntTNVVh/t3sn24183CaO9JyLLO+3
      SoxCPRxqJY8au/QM4h27IyYtIsfFMlVsNETr/eOfbKKKeXIMOh6SRe+JORRmSgJH
      /Qtty7S9A9+z4zMgPQhGtlFVEry9gqrAeJEL85LOsSPqTbc31yZ8b1pOpVBeClQj
      peRs+XokKWge67G8FjXgXaII2P2l53ivmfdlk6p0spZSP9rYkva96fJjAYwuum2r
      gXPAHrh+JU7ypioVZk2j+AOW4Ss01+jjSXWqF7Jl6t2MHGrYYl+XchweYscDlVRn
      37xdockqTvQK267gLZNZCzJYIDfoB94RzZSLgPQ07W0Mb/kucMH2RCOc4Gt2AdDQ
      eMmRWGUhrEdj2KoeIEWPvwP7jFRFqVZmb6r8O5SogSrdKH8SK/5dr6pkIoxVfVp3
      oYcXLk+qcAkYLFcnCEFa9wqq8jD/diR+LLp6ft48zkEkBPXo1F2nO1RXsspUo2OF
      QRw/9sQHE0ZZjAJEvc2ap7UjOuvl/GDNT2THe35Yt5M/gckjmKzpPVvY4Y02ZnyZ
      4edhGrtvCRGj743dwtxsohoEa4aVWl2o4lKlPu2jVaY0pWTvhwCN6R/cWaa4XLaB
      iXuhOFN8PZHE9EHuQSzbbaVY8hxnJKKa5G+cqwFnc8NR3Xb44Jmh8kCp5ttrJG3j
      2RN3wiW85ffECFl6oN5HPhFp7A==
      =Qktu
      -----END PGP MESSAGE-----
      """.trimIndent()

    val injector = getInjector()
    val pgpDecrypter = injector.getInstance(PgpDecrypterManager::class.java)["pgp_decrypter"]
    assertThrows<PGPException> {
      pgpDecrypter.decrypt(cypherText.toByteArray()).decodeToString()
    }
  }

  private fun getInjector(): Injector {
    val publicKeyContents = resourceLoader.utf8(publicKeyPath)!!
    val encryptKey = Key(
        "pgp_encrypter",
        KeyType.PGP_ENCRYPT,
        RealSecret(publicKeyContents)
    )

    val privateKeyContents = resourceLoader.utf8(privateKeyJsonPath)!!
    val jsonAdapter = moshi.adapter<PgpKeyJsonFile>()
    val keyJsonFile = jsonAdapter.fromJson(privateKeyContents)!!

    val kek = KmsEnvelopeAead(KeyReader.KEK_TEMPLATE, kmsClient.getAead(null))
    val toPlaintextSecretKey = Base64.getDecoder().decode(keyJsonFile.encrypted_private_key)
    val fakeEncryptedPrivateKey = kek.encrypt(toPlaintextSecretKey, null)

    val privateKeyJsonWithFakeEncryption = keyJsonFile.copy(
        encrypted_private_key = Base64.getEncoder().encodeToString(fakeEncryptedPrivateKey)
    )

    val decryptKey = Key(
        "pgp_decrypter",
        KeyType.PGP_DECRYPT,
        RealSecret(jsonAdapter.toJson(privateKeyJsonWithFakeEncryption))
    )

    val config = CryptoConfig(listOf(encryptKey, decryptKey), "test_master_key")
    return Guice.createInjector(
        CryptoTestModule(),
        CryptoModule(config),
        DeploymentModule.forTesting()
    )
  }

  companion object {
    internal const val publicKeyPath = "classpath:/misk-crypto-example-unencrypted-public-pgp.pgp"
    internal const val privateKeyJsonPath = "classpath:/misk-crypto-example-unencrypted-pgp.json"
  }
}
