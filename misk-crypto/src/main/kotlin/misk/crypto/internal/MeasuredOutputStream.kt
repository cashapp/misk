package misk.crypto.internal

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Either {@link #flush} or {@link #close} will represent an encrypt operation.
 */
class MeasuredOutputStream(
  private val key: misk.crypto.Key,
  private val os: OutputStream,
  private val metrics: KeyMetrics
  ) : OutputStream() {

  private val ln = AtomicInteger()

  override fun equals(other: Any?): Boolean {
    return os.equals(other)
  }

  override fun hashCode(): Int {
    return os.hashCode()
  }

  override fun toString(): String {
    return os.toString()
  }

  override fun close() {
    os.close().also {
      metrics.encrypted(key, ln.getAndSet(0))
    }
  }

  override fun flush() {
    os.flush().also {
      metrics.encrypted(key, ln.getAndSet(0))
    }
  }

  override fun write(b: Int) {
    os.write(b).also {
      ln.incrementAndGet()
    }
  }

  override fun write(b: ByteArray) {
    os.write(b).also {
      ln.addAndGet(b.size)
    }
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    super.write(b, off, len).also {
      // Presumably the bounds are already checked so being a bit willy nilly here.
      val avail = b.size - off
      val wrote = if (len > (b.size - off)) avail else len
      ln.addAndGet(wrote)
    }
  }

}
