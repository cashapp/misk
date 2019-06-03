package misk.crypto

import com.google.inject.Singleton

@Singleton
class AadProvider {

  private var aad: ByteArray = byteArrayOf()

  @Synchronized fun <T> setAad(aad: String, lambda: () -> T): T {
    this.aad = aad.toByteArray(Charsets.UTF_8)
    return lambda.invoke()
  }

  fun getAad(): ByteArray {
    return aad
  }
}