package misk.crypto

import misk.crypto.FieldLevelEncryptionPacket.ContextKey
import misk.inject.KAbstractModule

class FieldLevelEncryptionModule() : KAbstractModule() {
  companion object {

    // What to include with every AAD, indexable or not
    val CommonContextKeys = listOf(ContextKey.TABLE_NAME.name, ContextKey.COLUMN_NAME.name)

    // Whether to include the entirety of AAD in each encryption packet
    val IncludeCompleteAadInPacket = false
  }
}
