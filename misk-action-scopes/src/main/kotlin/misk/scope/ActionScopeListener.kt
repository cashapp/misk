package misk.scope

/**
 * A listener that can be called at various points within an [ActionScope]'s lifecycle. See the comment on each method
 * to understand when in the lifecycle it is called.
 */
interface ActionScopeListener {
  /**
   * Called during [ActionScope.close], immediately before the [ThreadLocal] that holds the [ActionScope.Instance] is
   * removed.
   *
   * The [ActionScope] is not closed until all the listeners are executed. That means that:
   * - Immediately before [onClose] is called, [ActionScope.inScope] returns true
   * - During [onClose], [ActionScope.inScope] returns true
   * - Immediately after all listeners have called [onClose], [ActionScope.inScope] returns false
   *
   * The [ActionScope] being closed or not is independent of the lifecycle of any action scoped values. A reference to
   * an object provided by an [ActionScopedProvider] can be held and used even if [ActionScope.inScope] returns false.
   * For example: an action-scoped HTTP Request Body may still be transmitting to the client despite the scope being
   * closed.
   */
  fun onClose()
}
