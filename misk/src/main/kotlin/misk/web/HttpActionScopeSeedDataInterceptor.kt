package misk.web

import com.google.inject.Key
import misk.Action
import misk.scope.ActionScope

/** Allows users to seed the [ActionScope] entered when handling an HTTP request. */
interface HttpActionScopeSeedDataInterceptor {
  /**
   * Accepts the current seed data, along with the [PathPattern] and [Action] for the [BoundAction]
   * that is being handled.
   *
   * Returns a new seed data map which is used to enter the [ActionScope].
   */
  fun intercept(
    seedData: Map<Key<*>, Any?>,
    pathPattern: PathPattern,
    action: Action,
  ): Map<Key<*>, Any?>

  // TODO(jeff) - Do we want this to follow the other Interceptor patterns and use a Factory?
}
