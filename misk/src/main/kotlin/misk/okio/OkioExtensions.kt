package misk.okio

import okio.BufferedSource

fun BufferedSource.forEachBlock(
    buffer: ByteArray,
    f: (buffer: ByteArray, bytesRead: Int) -> Unit
) {
  var bytesRead = read(buffer)
  while (bytesRead != -1) {
    f(buffer, bytesRead)
    bytesRead = read(buffer)
  }
}

fun BufferedSource.forEachBlock(
    blockSize: Int,
    f: (buffer: ByteArray, bytesRead: Int) -> Unit
) {
  forEachBlock(ByteArray(blockSize), f)
}
