package misk.okio

import okio.Buffer
import okio.BufferedSource
import okio.ByteString

fun BufferedSource.forEachBlock(buffer: ByteArray, f: (buffer: ByteArray, bytesRead: Int) -> Unit) {
  var bytesRead = read(buffer)
  while (bytesRead != -1) {
    f(buffer, bytesRead)
    bytesRead = read(buffer)
  }
}

fun BufferedSource.forEachBlock(blockSize: Int, f: (buffer: ByteArray, bytesRead: Int) -> Unit) {
  forEachBlock(ByteArray(blockSize), f)
}

fun BufferedSource.split(separator: ByteString): Sequence<Buffer> {
  val source = this
  return sequence {
    while (true) {
      val indexOf = indexOf(separator)
      if (indexOf >= 0) {
        val buffer = Buffer()
        source.read(buffer, indexOf)
        yield(buffer)
        source.skip(separator.size.toLong())
      } else {
        break
      }
    }
    val buffer = Buffer()
    source.readAll(buffer)
    yield(buffer)
  }
}
