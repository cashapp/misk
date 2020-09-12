package com.squareup.skim.crypto

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.daead.DeterministicAeadKeyTemplates
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.mac.MacKeyTemplates
import com.google.crypto.tink.signature.SignatureKeyTemplates
import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates
import misk.config.MiskConfig
import misk.crypto.ExternalKeyManager
import misk.crypto.Key
import misk.crypto.KeyAlias
import misk.crypto.KeyType
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class FakeExternalKeyManager : ExternalKeyManager {
  private var returnedKeysets: MutableMap<KeyAlias, Key> = mutableMapOf()

  override val allKeyAliases: Map<KeyAlias, KeyType>
    get() = returnedKeysets.map { it.key to it.value.key_type }.toMap()

  constructor(aliases: Map<KeyAlias, KeyType>) {
    aliases.forEach { (alias, type) ->
      val template = when (type) {
        KeyType.AEAD -> AeadKeyTemplates.AES256_GCM
        KeyType.DAEAD -> DeterministicAeadKeyTemplates.AES256_SIV
        KeyType.STREAMING_AEAD -> StreamingAeadKeyTemplates.AES128_GCM_HKDF_4KB
        KeyType.MAC -> MacKeyTemplates.HMAC_SHA256_128BITTAG
        KeyType.DIGITAL_SIGNATURE -> SignatureKeyTemplates.ECDSA_P256
        KeyType.HYBRID_ENCRYPT -> HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM
        KeyType.HYBRID_ENCRYPT_DECRYPT -> HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM
        else -> DeterministicAeadKeyTemplates.AES256_SIV
      }
      val handle = KeysetHandle.generateNew(template)

      val baos = ByteArrayOutputStream()
      val writer = JsonKeysetWriter.withOutputStream(baos)

      CleartextKeysetHandle.write(handle, writer)
      returnedKeysets[alias] =
          Key(alias, KeyType.DAEAD, MiskConfig.RealSecret(baos.toString(Charset.defaultCharset())))
    }
  }

  constructor(rawKeys: List<Key>) {
    rawKeys.forEach {
      returnedKeysets[it.key_name] = it
    }
  }

  override fun getKeyByAlias(alias: KeyAlias): Key? {
    return returnedKeysets[alias]
  }

  override fun onKeyUpdated(cb: (KeyAlias, KeysetHandle) -> Unit) = false
}
