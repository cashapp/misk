package misk.eventrouter

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.collect.LinkedHashMultimap
import com.squareup.moshi.JsonAdapter
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

internal class RealEventRouter : EventRouter {
  @Inject lateinit var eventChannel: ClusterConnector
  @Inject lateinit var eventJsonAdapter : JsonAdapter<SocketEvent>
  @Inject @ForEventRouterSubscribers lateinit var executor: ExecutorService

  lateinit var clusterSnapshot: ClusterSnapshot
  private val subscriptionQueue = ArrayDeque<QueuedSubscribe<*>>()
  private val publishQueue = ArrayDeque<QueuedPublish>()

  // TODO: concurrent access to this stuff?
  private val localSubscribers = LinkedHashMultimap.create<String, LocalSubscription<*>>()
  private val remoteSubscribers = LinkedHashMultimap.create<String, WebSocket>()
  private val hostToSockets = CacheBuilder.newBuilder()
      .build<String, WebSocket>(object : CacheLoader<String, WebSocket>() {
        override fun load(topicOwner: String): WebSocket {
          return eventChannel.connectSocket(topicOwner, webSocketListener)
        }
      })

  val webSocketListener = object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
      val event = eventJsonAdapter.fromJson(text)!!
      val (topic, message) = event
      if (message == "subscribe") {
        remoteSubscribers.put(topic, webSocket)
        return
      }

      // TODO: unsubscribe
      // TODO: type safety or something for the string "subscribe"

      localSubscribers.get(topic).forEach {
        it.enqueue(executor, LocalSubscription.Action.Event(message))
      }

      remoteSubscribers.get(topic).forEach {
        it.send(eventJsonAdapter.toJson(event))
      }
    }
  }

  private val topicPeer = object : TopicPeer {
    override fun acceptWebSocket(webSocket: WebSocket): WebSocketListener = webSocketListener

    override fun clusterChanged(clusterSnapshot: ClusterSnapshot) {
      this@RealEventRouter.clusterSnapshot = clusterSnapshot
      initCluster()
    }
  }

  /** We queue up subscribes when we don't have a cluster. */
  private fun initCluster() {
    while (subscriptionQueue.isNotEmpty()) {
      val queuedSubscribe = subscriptionQueue.pop()
      subscribe(queuedSubscribe)
    }

    while (publishQueue.isNotEmpty()) {
      val (topic, event) = publishQueue.pop()
      publish(topic, event)
    }
  }

  fun joinCluster() {
    eventChannel.joinCluster(topicPeer)
  }

  fun leaveCluster() {
    eventChannel.leaveCluster(topicPeer)
  }

  override fun <T : Any> getTopic(name: String): Topic<T> {
    return object : Topic<T> {
      override val name: String
        get() = name

      override fun publish(event: T) {
        this@RealEventRouter.publish(name, event)
      }

      override fun subscribe(listener: Listener<T>): Subscription<T> {
        return this@RealEventRouter.subscribe(this, listener)
      }
    }
  }

  private fun <T : Any> publish(topic: String, event: T) {
    if (!::clusterSnapshot.isInitialized) {
      publishQueue.add(QueuedPublish(topic, event))
      return
    }

    val topicOwner = clusterSnapshot.topicToHost(topic)

    val e = SocketEvent(topic, event.toString())
    hostToSockets[topicOwner].send(eventJsonAdapter.toJson(e))
  }

  private fun <T : Any> subscribe(queuedSubscribe: QueuedSubscribe<T>): Subscription<T> =
      subscribe(queuedSubscribe.topic, queuedSubscribe.listener)

  private fun <T : Any> subscribe(topic: Topic<T>, listener: Listener<T>): Subscription<T> {
    val localSubscription = LocalSubscription(listener, topic)
    if (!::clusterSnapshot.isInitialized) {
      subscriptionQueue.add(QueuedSubscribe(topic, listener))
      return localSubscription
    }

    val topicOwner = clusterSnapshot.topicToHost(topic.name)
    val socket = eventChannel.connectSocket(topicOwner, webSocketListener)
    localSubscribers.put(topic.name, localSubscription)

    if (clusterSnapshot.peerToHost(topicPeer) != topicOwner) {
      socket.send(eventJsonAdapter.toJson(SocketEvent(topic.name, "subscribe")))
    }
    localSubscription.enqueue(executor, LocalSubscription.Action.Open)
    return localSubscription
  }
}

internal class LocalSubscription<T : Any>(
  private val listener: Listener<T>,
  override val topic: Topic<T>
) : Runnable, Subscription<T> {

  private val queue = LinkedBlockingQueue<Action>()
  private val running = AtomicBoolean()

  fun enqueue(executorService: ExecutorService, action: Action) {
    queue.add(action)
    executorService.execute(this)
  }

  override fun run() {
    if (!running.compareAndSet(false, true)) return

    while (true) {
      val action = queue.poll()

      if (action == null) {
        running.set(false)
        if (queue.isNotEmpty() && !running.compareAndSet(false, true)) continue
        return
      }

      when (action) {
        is Action.Event -> listener.onEvent(this, action.body as T)
        is Action.Open -> listener.onOpen(this)
        is Action.Close -> listener.onClose(this)
      }
    }
  }

  override fun cancel() {
    TODO()
  }

  sealed class Action {
    data class Event(val body: Any) : Action()
    object Open : Action()
    object Close : Action()
  }
}

internal data class QueuedSubscribe<T : Any>(
  val topic: Topic<T>,
  val listener: Listener<T>
)

internal data class QueuedPublish(
  val topic: String,
  val event: Any
)

internal data class SocketEvent(
  val topic: String,
  val message: String
)
