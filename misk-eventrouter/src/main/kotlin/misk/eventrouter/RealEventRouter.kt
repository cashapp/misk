package misk.eventrouter

import com.google.common.collect.LinkedHashMultimap
import com.squareup.moshi.JsonAdapter
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import wisp.logging.getLogger
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

private val logger = getLogger<RealEventRouter>()

internal class RealEventRouter @Inject constructor() : EventRouter {
  @Inject lateinit var clusterConnector: ClusterConnector
  @Inject lateinit var eventJsonAdapter: JsonAdapter<SocketEvent>
  @Inject lateinit var clusterMapper: ClusterMapper
  @Inject @ForEventRouterActions lateinit var actionExecutor: ExecutorService
  @Inject @ForEventRouterSubscribers lateinit var subscriberExecutor: ExecutorService

  internal lateinit var clusterSnapshot: ClusterSnapshot
  internal val hasClusterSnapshot = AtomicBoolean()
  private val localSubscribers = LinkedHashMultimap.create<String, LocalSubscriber<*>>()
  private val remoteSubscribers = LinkedHashMultimap.create<String, WebSocket>()
  private val actionQueue = LinkedBlockingQueue<Action>()
  private var hasJoinedCluster = AtomicBoolean()
  private var hostsToSockets = mapOf<String, WebSocket>()

  sealed class Action {
    data class OnMessage(val webSocket: WebSocket, val text: String) : Action()
    data class ClusterChanged(val newSnapshot: ClusterSnapshot) : Action()
    data class Publish(val topic: String, val event: Any) : Action()
    data class Subscribe(val localSubscription: LocalSubscriber<*>) : Action()
    data class CancelSubscription(val localSubscription: LocalSubscriber<*>) : Action()
    data class ClosedWebSocket(val webSocket: WebSocket) : Action()
    object LeaveCluster : Action()
  }

  internal val webSocketListener = object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
      enqueue(Action.OnMessage(webSocket, text))
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String?) {
      enqueue(Action.ClosedWebSocket(webSocket))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String?) {
      enqueue(Action.ClosedWebSocket(webSocket))
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable) {
      enqueue(Action.ClosedWebSocket(webSocket))
    }
  }

  private val topicPeer = object : TopicPeer {
    override fun acceptWebSocket(webSocket: WebSocket): WebSocketListener = webSocketListener

    override fun clusterChanged(clusterSnapshot: ClusterSnapshot) {
      if (hasClusterSnapshot.compareAndSet(false, true)) {
        this@RealEventRouter.clusterSnapshot = clusterSnapshot
        logger.debug { "cluster changed: $clusterSnapshot" }
        actionExecutor.execute({ drainQueue() })
      } else {
        enqueue(Action.ClusterChanged(clusterSnapshot))
      }
    }
  }

  internal fun drainQueue() {
    // If we don't have a cluster snapshot yet return. This function will be
    // called again when we get one.
    if (!hasClusterSnapshot.get()) return

    while (true) {
      val action = actionQueue.poll() ?: return

      when (action) {
        is Action.LeaveCluster -> handleLeaveCluster()
        is Action.ClusterChanged -> handleClusterChanged(action)
        is Action.OnMessage -> handleOnMessage(action)
        is Action.Subscribe -> handleSubscribe(action)
        is Action.Publish -> handlePublish(action)
        is Action.CancelSubscription -> handleCancelSubscription(action)
        is Action.ClosedWebSocket -> handleClosedWebSocket(action)
      }

      logger.debug {
        "current state:[localSubscribers=$localSubscribers] " +
          "[remoteSubscribers=$remoteSubscribers] [hostToSocket=${hostsToSockets.keys}]"
      }
    }
  }

  private fun handleCancelSubscription(action: Action.CancelSubscription) {
    logger.debug { "cancel subscription: ${action.localSubscription}" }

    val topicName = action.localSubscription.topic.name
    val localTopicSubscribers = localSubscribers.get(topicName)
    localTopicSubscribers.remove(action.localSubscription)
    if (localTopicSubscribers.isEmpty()) {
      val topicOwner = clusterMapper.topicToHost(clusterSnapshot, topicName)
      if (topicOwner != clusterSnapshot.self) {
        val unsubscribeEvent = eventJsonAdapter.toJson(
          SocketEvent.Unsubscribe(topicName)
        )
        hostToSocket(topicOwner).send(unsubscribeEvent)
      }
    }
    action.localSubscription.onClose()
  }

  private fun handlePublish(action: Action.Publish) {
    logger.debug { "onPublish: ${action.event}" }

    val topicOwner = clusterMapper.topicToHost(clusterSnapshot, action.topic)
    val socketEvent = SocketEvent.Event(action.topic, action.event.toString())
    val eventJson = eventJsonAdapter.toJson(socketEvent)

    if (topicOwner != clusterSnapshot.self) {
      hostToSocket(topicOwner).send(eventJson)
    } else {
      remoteSubscribers.get(action.topic).forEach { it.send(eventJson) }
      localSubscribers.get(action.topic).forEach { it.onEvent(socketEvent.message) }
    }
  }

  private fun handleSubscribe(action: Action.Subscribe) {
    logger.debug { "onSubscribe: ${action.localSubscription}" }

    val topicName = action.localSubscription.topic.name
    val topicOwner = clusterMapper.topicToHost(clusterSnapshot, topicName)
    if (topicOwner != clusterSnapshot.self) {
      val subscribeEvent = eventJsonAdapter.toJson(
        SocketEvent.Subscribe(topicName)
      )
      hostToSocket(topicOwner).send(subscribeEvent)
    } else {
      action.localSubscription.onOpen()
    }

    localSubscribers.put(topicName, action.localSubscription)
  }

  private fun handleOnMessage(action: Action.OnMessage) {
    logger.debug { "onMessage: ${action.text}" }

    val socketEvent = eventJsonAdapter.fromJson(action.text)!!
    when (socketEvent) {
      is SocketEvent.Event -> {
        localSubscribers.get(socketEvent.topic).forEach {
          it.onEvent(socketEvent.message)
        }

        remoteSubscribers.get(socketEvent.topic).forEach {
          it.send(action.text)
        }
      }

      is SocketEvent.Subscribe -> {
        remoteSubscribers.put(socketEvent.topic, action.webSocket)
        action.webSocket.send(
          eventJsonAdapter.toJson(
            SocketEvent.Ack(socketEvent.topic)
          )
        )
      }

      is SocketEvent.Ack -> {
        localSubscribers.get(socketEvent.topic).forEach {
          it.onOpen()
        }
      }

      is SocketEvent.Unsubscribe -> {
        remoteSubscribers.get(socketEvent.topic).remove(action.webSocket)
      }
      else -> Unit
    }
  }

  private fun handleClusterChanged(action: Action.ClusterChanged) {
    logger.debug { "handleClusterChanged: ${action.newSnapshot}" }

    val topics = remoteSubscribers.keySet().plus(localSubscribers.keySet())
    for (topic in topics) {
      val localSubscribers = localSubscribers.get(topic)
      val websockets = remoteSubscribers.get(topic)

      if (clusterMapper.topicToHost(clusterSnapshot, topic) !=
        clusterMapper.topicToHost(action.newSnapshot, topic)
      ) {

        val iterator = localSubscribers.iterator()
        while (iterator.hasNext()) {
          val localSubscription = iterator.next()
          localSubscription.cancel()
          iterator.remove()
        }

        val wsIter = websockets.iterator()
        while (wsIter.hasNext()) {
          val next = wsIter.next()
          next.close(1000, "the topic owner has changed")
          wsIter.remove()
        }
      }
    }

    clusterSnapshot = action.newSnapshot
  }

  private fun handleClosedWebSocket(action: Action.ClosedWebSocket) {
    logger.debug { "web socket closed: ${action.webSocket}" }

    // TODO(tso): handle this more efficiently?
    // this looks a lot like cluster changed. Maybe share code?
    val hostname =
      hostsToSockets.entries.firstOrNull { it.value == action.webSocket }?.key ?: return
    hostsToSockets = hostsToSockets.minus(hostname)

    val topics = localSubscribers.keySet()
    for (topic in topics) {
      val localSubscribers = localSubscribers.get(topic)

      if (clusterMapper.topicToHost(clusterSnapshot, topic) == hostname) {
        val iterator = localSubscribers.iterator()
        while (iterator.hasNext()) {
          val localSubscription = iterator.next()
          localSubscription.onClose()
          iterator.remove()
        }
      }
    }
  }

  private fun handleLeaveCluster() {
    logger.debug { "handleLeaveCluster" }
    clusterConnector.leaveCluster(topicPeer)
    for (localSubscriber in localSubscribers.values()) {
      localSubscriber.onClose()
    }
  }

  private fun hostToSocket(hostname: String): WebSocket {
    logger.debug { "connecting to $hostname" }
    val ws = hostsToSockets[hostname]
    if (ws == null) {
      hostsToSockets = hostsToSockets.plus(
        Pair(hostname, clusterConnector.connectSocket(hostname, webSocketListener))
      )
    }
    return hostsToSockets[hostname]!!
  }

  fun joinCluster() {
    if (hasJoinedCluster.compareAndSet(false, true)) {
      clusterConnector.joinCluster(topicPeer)
    }
  }

  fun leaveCluster() {
    if (hasJoinedCluster.compareAndSet(true, false)) {
      enqueue(Action.LeaveCluster)
    }
  }

  override fun <T : Any> getTopic(name: String): Topic<T> {
    return object : Topic<T> {
      override val name: String
        get() = name

      override fun publish(event: T) {
        enqueue(Action.Publish(name, event))
      }

      override fun subscribe(listener: Listener<T>): Subscription<T> {
        val localSubscription =
          LocalSubscriber(
            listener, this@RealEventRouter, subscriberExecutor,
            this
          )
        enqueue(Action.Subscribe(localSubscription))
        return localSubscription
      }
    }
  }

  internal fun enqueue(action: Action) {
    actionQueue.add(action)
    actionExecutor.execute({ drainQueue() })
  }
}

// TODO(tso): add 3 subscriber types:
// - RemotePublisher (someone I'm subscribed to)
// - A local subscriber
// - A remote subscriber

internal data class LocalSubscriber<T : Any>(
  private val listener: Listener<T>,
  private val realEventRouter: RealEventRouter,
  private val executorService: ExecutorService,
  override val topic: Topic<T>
) : Subscription<T> {
  fun onOpen() {
    executorService.execute({
      this.listener.onOpen(this)
    })
  }

  fun onEvent(message: String) {
    executorService.execute({
      @Suppress("UNCHECKED_CAST")
      (listener as Listener<String>).onEvent(this as Subscription<String>, message)
    })
  }

  fun onClose() {
    executorService.execute({
      this.listener.onClose(this)
    })
  }

  override fun cancel() {
    realEventRouter.enqueue(RealEventRouter.Action.CancelSubscription(this))
  }

  override fun toString(): String {
    return "LocalSubscription[$listener]"
  }
}
