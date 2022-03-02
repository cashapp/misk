package misk.concurrent

import com.google.common.util.concurrent.ForwardingListeningExecutorService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** [ListeningExecutorService] which wraps all calls */
abstract class WrappingListeningExecutorService : ForwardingListeningExecutorService() {
  /** Wraps the specified callable and returns the new wrapped one. */
  protected abstract fun <T> wrap(callable: Callable<T>): Callable<T>

  override fun <T> submit(callable: Callable<T>): ListenableFuture<T> {
    val wrapped = wrap(callable)
    return delegate().submit<T>(wrapped)
  }

  override fun <T> submit(runnable: Runnable, result: T): ListenableFuture<T> {
    return submit<T> { runnable.run(); result }
  }

  override fun submit(runnable: Runnable): ListenableFuture<*> {
    val wrapped = wrap<Unit>(Callable { runnable.run() })
    return delegate().submit(wrapped)
  }

  @Throws(InterruptedException::class)
  override fun <T> invokeAll(callables: Collection<Callable<T>>): List<Future<T>> {
    return delegate().invokeAll(callables.map { wrap(it) })
  }

  @Throws(InterruptedException::class)
  override fun <T> invokeAll(
    callables: Collection<Callable<T>>,
    timeout: Long,
    timeUnit: TimeUnit
  ): List<Future<T>> {
    return delegate().invokeAll(callables.map { wrap(it) }, timeout, timeUnit)
  }

  @Throws(InterruptedException::class, ExecutionException::class)
  override fun <T> invokeAny(callables: Collection<Callable<T>>): T {
    return delegate().invokeAny(callables.map { wrap(it) })
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  override fun <T> invokeAny(
    callables: Collection<Callable<T>>,
    timeout: Long,
    timeUnit: TimeUnit
  ): T {
    return delegate().invokeAny(callables.map { wrap(it) }, timeout, timeUnit)
  }

  override fun execute(runnable: Runnable) {
    delegate().submit(wrap<Unit>(Callable { runnable.run() }))
  }
}
