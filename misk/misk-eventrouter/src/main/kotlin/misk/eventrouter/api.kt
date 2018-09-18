package misk.eventrouter

interface EventRouter {
  fun <T : Any> getTopic(name: String): Topic<T>
}

interface Topic<T : Any> {
  val name: String
  fun publish(event: T)
  fun subscribe(listener: Listener<T>): Subscription<T>
}

interface Subscription<T : Any> {
  val topic: Topic<T>
  fun cancel()
}

interface Listener<T : Any> {
  fun onOpen(subscription: Subscription<T>)
  fun onEvent(subscription: Subscription<T>, event: T)
  fun onClose(subscription: Subscription<T>)
}
