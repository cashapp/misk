package misk.hash

import com.google.common.hash.HashCode
import okio.ByteString

/** @return the [HashCode] as a [ByteString] */
fun HashCode.asByteString(): ByteString = ByteString.of(*asBytes())
