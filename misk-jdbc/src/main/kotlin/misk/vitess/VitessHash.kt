package misk.vitess

import com.google.common.primitives.Longs
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okio.ByteString

import com.google.common.base.Preconditions.checkState

/*
Port of:
go/vt/vtgate/vindexes/hash.go

Copyright 2017 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/

internal object VitessHash {
  private val NULL_KEY = SecretKeySpec(ByteArray(24), "DESede")
  private val NULL_SALT = IvParameterSpec(ByteArray(8))
  private val TRIPLE_DES = "DESede/CBC/NoPadding"

  /**
   * Returns the "keyspace ID" of the id. The keyspace ID is used by Vitess to map an id to a
   * shard.
   */
  fun toKeyspaceId(id: Long): ByteString {
    try {
      val tripleDes = Cipher.getInstance(TRIPLE_DES)
      checkState(tripleDes.blockSize == 8)
      tripleDes.init(Cipher.ENCRYPT_MODE, NULL_KEY, NULL_SALT)
      return ByteString.of(*tripleDes.doFinal(Longs.toByteArray(id)))
    } catch (e: GeneralSecurityException) {
      throw AssertionError("Should be fine", e)
    }
  }

  /*
Port of:
go/vt/vtgate/vindexes/hash.go

func vunhash(k []byte) (uint64, error) {
  if len(k) != 8 {
    return 0, fmt.Errorf("invalid keyspace id: %v", hex.EncodeToString(k))
  }
  var unhashed [8]byte
  block3DES.Decrypt(unhashed[:], k)
  return binary.BigEndian.Uint64(unhashed[:]), nil
}
*/
  fun fromKeyspaceId(keyspaceId: ByteString): Long {
    try {
      val tripleDes = Cipher.getInstance(TRIPLE_DES)
      checkState(tripleDes.blockSize == 8)
      tripleDes.init(Cipher.DECRYPT_MODE, NULL_KEY, NULL_SALT)
      val plaintextBlock = tripleDes.doFinal(keyspaceId.toByteArray())
      checkState(plaintextBlock.size == 8)
      return Longs.fromByteArray(plaintextBlock)
    } catch (e: GeneralSecurityException) {
      throw AssertionError("Should be fine", e)
    }
  }
}
