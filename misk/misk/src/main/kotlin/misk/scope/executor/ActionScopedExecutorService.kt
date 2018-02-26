package misk.scope.executor

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import misk.concurrent.WrappingListeningExecutorService
import misk.scope.ActionScope
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService

/**
 * Wraps a [ListeningExecutorService] to propagate the current action scope to any tasks
 * submitted by the current thread
 */
class ActionScopedExecutorService(
  target: ExecutorService,
  private val scope: ActionScope
) : WrappingListeningExecutorService() {

  private val target = MoreExecutors.listeningDecorator(target)

  override fun <T> wrap(callable: Callable<T>): Callable<T> = scope.propagate(callable)

  override fun delegate(): ListeningExecutorService = target
}
