package misk.crypto.internal

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class MeasuredInputStream(
  private val key: misk.crypto.Key,
  private val ist: InputStream,
  private val metrics: KeyMetrics
): InputStream() {

  private val ln = AtomicInteger()

  override fun equals(other: Any?): Boolean {
    return ist.equals(other)
  }

  override fun hashCode(): Int {
    return ist.hashCode()
  }

  override fun toString(): String {
    return ist.toString()
  }

  override fun close() {
    super.close().also {
      metrics.decrypted(key, ln.getAndSet(0))
    }
  }

  override fun reset() {
    super.reset()
  }

  override fun read(): Int {
    return ist.read().also {
      if (it != -1)
        ln.incrementAndGet()
    }
  }

  override fun read(b: ByteArray): Int {
    return ist.read(b).also {
      if (it != -1)
        ln.addAndGet(it)
    }
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    return ist.read(b, off, len).also {
      if (it != -1)
        ln.addAndGet(it)
    }
  }

  override fun readAllBytes(): ByteArray {
    return ist.readAllBytes().also {
      ln.addAndGet(it.size)
    }
  }

  override fun readNBytes(len: Int): ByteArray {
    return ist.readNBytes(len).also {
      ln.addAndGet(it.size)
    }
  }

  override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int {
    return ist.readNBytes(b, off, len).also {
      if (it != -1)
        ln.addAndGet(it)
    }
  }

  override fun skip(n: Long): Long {
    return ist.skip(n)
  }

  override fun available(): Int {
    return ist.available()
  }

  override fun mark(readlimit: Int) {
    ist.mark(readlimit)
  }

  override fun markSupported(): Boolean {
    return ist.markSupported()
  }

  override fun transferTo(out: OutputStream?): Long {
    return ist.transferTo(out)
  }

}
