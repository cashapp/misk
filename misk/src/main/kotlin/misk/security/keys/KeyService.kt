package misk.security.keys

import okio.ByteString

/** Handles encryption and decryption using keys stored in a key management service */
interface KeyService {
  /** encrypts the provided plain text using the given stored key */
  fun encrypt(
      keyAlias: String,
      plainText: ByteString
  ): ByteString

  /** decrypts the provided cipher text using the given stored key */
  fun decrypt(
      keyAlias: String,
      cipherText: ByteString
  ): ByteString
}
