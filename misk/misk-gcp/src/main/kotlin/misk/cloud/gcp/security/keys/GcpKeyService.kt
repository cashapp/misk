package misk.cloud.gcp.security.keys

import com.google.api.services.cloudkms.v1.CloudKMS
import com.google.api.services.cloudkms.v1.model.DecryptRequest
import com.google.api.services.cloudkms.v1.model.EncryptRequest
import misk.security.keys.KeyService
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import javax.inject.Inject

internal class GcpKeyService @Inject internal constructor(
  kms: CloudKMS,
  val config: GcpKmsConfig
) : KeyService {
  val cryptoKeys = kms.projects().locations().keyRings().cryptoKeys()

  override fun encrypt(keyAlias: String, plainText: ByteString): ByteString {
    val keyLocation = config.key_locations[keyAlias]
        ?: throw IllegalArgumentException("no location for keyAlias $keyAlias")
    val resource = "projects/${config.project_id}/${keyLocation.path}"
    val request = EncryptRequest().encodePlaintext(plainText.toByteArray())
    val response = cryptoKeys.encrypt(resource, request).execute()
    return ByteBuffer.wrap(response.decodeCiphertext()).toByteString()
  }

  override fun decrypt(keyAlias: String, cipherText: ByteString): ByteString {
    val keyLocation = config.key_locations[keyAlias]
        ?: throw IllegalArgumentException("no location for key $keyAlias")
    val resource = "projects/${config.project_id}/${keyLocation.path}"
    val request = DecryptRequest().encodeCiphertext(cipherText.toByteArray())
    val response = cryptoKeys.decrypt(resource, request).execute()
    return ByteBuffer.wrap(response.decodePlaintext()).toByteString()
  }
}
