package misk.opentelemetry

import com.google.common.util.concurrent.ServiceManager
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Counts Misk applications whose services are running, so the SDK shutdown hook can wait for them to finish stopping.
 * The JVM runs shutdown hooks concurrently; without waiting, the SDK could close while services still drain.
 */
internal object RunningApplications {
  private val lock = Object()
  private var running = 0

  fun started() = synchronized(lock) { running++ }

  fun stopped() =
    synchronized(lock) {
      running--
      lock.notifyAll()
    }

  /** Waits until no application is running, or [timeout] passes; returns whether every application stopped. */
  fun awaitStopped(timeout: Duration): Boolean {
    val deadline = System.nanoTime() + timeout.toNanos()
    synchronized(lock) {
      while (running > 0) {
        val remainingMs = (deadline - System.nanoTime()) / 1_000_000
        if (remainingMs <= 0) return false
        lock.wait(remainingMs)
      }
    }
    return true
  }
}

/** Counts its application as running from when all services are healthy until they all reach a terminal state. */
internal class RunningApplicationListener : ServiceManager.Listener() {
  private val counted = AtomicBoolean()

  override fun healthy() {
    if (counted.compareAndSet(false, true)) RunningApplications.started()
  }

  override fun stopped() {
    if (counted.compareAndSet(true, false)) RunningApplications.stopped()
  }
}
