package misk.cloud.gcp.security.encryption

interface EnvelopeEncryptor {
  fun encrypt(payloadToEncrypt: ByteArray): ByteArray
}
