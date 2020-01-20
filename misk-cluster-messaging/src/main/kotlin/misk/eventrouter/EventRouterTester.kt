package misk.eventrouter

import javax.inject.Inject

class EventRouterTester @Inject constructor() {
  @Inject private lateinit var fakeClusterConnector: FakeClusterConnector

  @Inject
  @ForEventRouterActions
  private lateinit var actionExecutor: QueueingExecutorService

  @Inject
  @ForEventRouterSubscribers
  private lateinit var subscriberExecutor: QueueingExecutorService

  fun processEverything() {
    do {
      var total = 0
      total += fakeClusterConnector.processEverything()
      total += actionExecutor.processEverything()
      total += subscriberExecutor.processEverything()
    } while (total > 0)
  }
}
