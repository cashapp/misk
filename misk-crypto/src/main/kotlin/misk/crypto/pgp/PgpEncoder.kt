package misk.crypto.pgp

/**
 * A [PgpEncrypter] is tied to a public PGP key which it uses to encrypt
 * messages.
 */
interface PgpEncrypter {
  /**
   * Encrypts the given byte array. Armored refers to whether to output in
   * a text format or not.
   *
   * For example, with armored we get output like:
   *
   * -----BEGIN PGP MESSAGE-----
   * Version: BCPG C# v1.6.1.0
   *
   * hQIMA0tM4ZUzpKCkAQ//ZO8hVhp6LMxshCLqcqgPkXcU1kBHPQjiUBs6QPxaQFcg
   * PIXJEdZWQ1RkMUKdCfzjvevizi/2NWAwZ797fELdbpVyH6JI2Me1Ov10f8qgYvbG
   * V0GJ3loRt84KjKdVocKguJVfMvOeMVJanPpxfPt3Ak+Nt/voZHmJdTkV4cjWTmcU
   * x31xqc5LZcvgF4Lv+ZcMw090b281yMENwALnvQJ9FQg5WTfZ+YautUMwWGk8W8XM
   * H3tYkdh3jKizy8YTw+zfVDn1yhw2BgSFp0QIgHIQQ2Q2gyknRfj4zdfs/4bZYcu1
   * KNgj9op/IejZNstP9JWw4labC78nTIW5s1f0LWF5hhZUkxeAOEyVluUVJgEw44B4
   * LKb0p/8jb4J/dVrsmaTDOONZ48wtNjZOTV61DhCnn+UiNVSfQe6I7XMtFpEH0PN4
   * WzermmeEgjw1MADeh0jg/9wMM/p9UnTxihGQrPRgHYUu45jI3ys3qArsD2GvAi9y
   * QYqsuRJNB7EEu2clBRASA3zoWkpqbs3H9S2fB9Sf9ZzZTPCzowOdK1oe0vlMVt3A
   * mCILe05s2nowwBrBWu4PHUTkllsnLliHhrihu9sqXCHy6u1pdNQMvJgY6kbgQmsG
   * bVY+brdkLdQ1BxbTT1YixzLm//zqMqGC1ZJUsr5PAIbNtyKK6u6+mCkZj0Y3fU3J
   * Il0x5V09QcC9AHyxJ8VMOxzhQZjwVYNtD6SGeY8jOyfUScY=
   * =DHKq
   * -----END PGP MESSAGE-----
   *
   * Without the armored encoding we would get binary data which isn't
   * viewable in text format.
   */
  fun encrypt(plaintext: ByteArray, armored: Boolean): ByteArray
}

/**
 * A [PgpDecrypter] is tied to a private PGP key which it uses to decrypt
 * messages.
 */
interface PgpDecrypter {
  fun decrypt(ciphertext: ByteArray): ByteArray
}

internal data class PgpKeyJsonFileMetadata(
  val name: String,
  val email: String,
  val comment: String
)

internal data class PgpKeyJsonFile(
  val region: String,
  val encrypted_private_key: String,
  val public_key: String,
  val pgp: PgpKeyJsonFileMetadata,
  val aws_kms_key_id: String
)