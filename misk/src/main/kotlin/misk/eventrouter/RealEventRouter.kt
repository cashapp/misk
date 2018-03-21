package misk.eventrouter

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.collect.LinkedHashMultimap
import com.squareup.moshi.JsonAdapter
import misk.logging.getLogger
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

private val logger = getLogger<RealEventRouter>()

internal class RealEventRouter : EventRouter {
  @Inject lateinit var clusterConnector: ClusterConnector
  @Inject lateinit var eventJsonAdapter: JsonAdapter<SocketEvent>
  @Inject lateinit var clusterMapper: ClusterMapper
  @Inject @ForEventRouterSubscribers lateinit var executor: ExecutorService

  internal lateinit var clusterSnapshot: ClusterSnapshot
  private val actionQueue = LinkedBlockingQueue<Action>()
  private val localSubscribers = LinkedHashMultimap.create<String, LocalSubscription<*>>()
  private val remoteSubscribers = LinkedHashMultimap.create<String, WebSocket>()
  private val hasClusterSnapshot = AtomicBoolean()

  // TODO(tso): Change this to LinkedHashMap and use computeIfAbsent
  private val hostToSocket = CacheBuilder.newBuilder()
      .build<String, WebSocket>(object : CacheLoader<String, WebSocket>() {
        override fun load(hostname: String): WebSocket {
          logger.debug { "[${clusterSnapshot.self}]: connecting to $hostname" }
          return clusterConnector.connectSocket(hostname, webSocketListener)
        }
      })

  sealed class Action {
    class OnMessage(val webSocket: WebSocket, val text: String) : Action()
    class ClusterChanged(val newSnapshot: ClusterSnapshot) : Action()
    class Publish(val topic: String, val event: Any) : Action()
    class Subscribe(val localSubscription: LocalSubscription<*>) : Action()
    class CancelSubscription(val localSubscription: LocalSubscription<*>) : Action()
    object LeaveCluster : Action()
  }

  private val webSocketListener = object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
      enqueue(Action.OnMessage(webSocket, text))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String?) {
      TODO("Enqueue Close")
    }
  }

  private val topicPeer = object : TopicPeer {
    override fun acceptWebSocket(webSocket: WebSocket): WebSocketListener = webSocketListener

    override fun clusterChanged(clusterSnapshot: ClusterSnapshot) {
      if (hasClusterSnapshot.compareAndSet(false, true)) {
        this@RealEventRouter.clusterSnapshot = clusterSnapshot
        logger.debug { "[${clusterSnapshot.self}]: cluster changed: $clusterSnapshot" }
        executor.submit({ drainQueue() })
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

      logger.debug { "[${clusterSnapshot.self}]: processing action: $action" }
      when (action) {
        is Action.LeaveCluster -> clusterConnector.leaveCluster(topicPeer)

        is Action.ClusterChanged -> {
          if (action.newSnapshot.version - clusterSnapshot.version > 1) {
            TODO("We've missed a cluster version and now everything is terrible")
          }

          val topics = remoteSubscribers.keySet().plus(localSubscribers.keySet())
          for (topic in topics) {
            val localSubscribers = localSubscribers.get(topic)
            val websockets = remoteSubscribers.get(topic)

            if (clusterMapper.topicToHost(clusterSnapshot, topic) !=
                clusterMapper.topicToHost(action.newSnapshot, topic)) {
              localSubscribers.forEach {
                it.onClose()
                localSubscribers.remove(it)
              }
              websockets.forEach {
                it.send(eventJsonAdapter.toJson(SocketEvent.Unsubscribe(topic)))
              }
            }
          }

          clusterSnapshot = action.newSnapshot
        }

        is Action.OnMessage -> {
          val socketEvent = eventJsonAdapter.fromJson(action.text)!!
          when (socketEvent) {
            is SocketEvent.Event -> {
              localSubscribers.get(socketEvent.topic).forEach {
                (it.listener as Listener<String>).onEvent(it as Subscription<String>,
                    socketEvent.message)
              }

              remoteSubscribers.get(socketEvent.topic).forEach {
                it.send(action.text)
              }
            }

            is SocketEvent.Subscribe -> {
              remoteSubscribers.put(socketEvent.topic, action.webSocket)
              action.webSocket.send(eventJsonAdapter.toJson(SocketEvent.Ack(socketEvent.topic)))
            }

            is SocketEvent.Ack -> {
              localSubscribers.get(socketEvent.topic).forEach {
                it.onOpen()
              }
            }

            is SocketEvent.Unsubscribe -> {
              remoteSubscribers.get(socketEvent.topic).remove(action.webSocket)
            }
          }
        }

        is Action.Subscribe -> {
          val topicName = action.localSubscription.topic.name
          val topicOwner = clusterMapper.topicToHost(clusterSnapshot, topicName)
          if (topicOwner != clusterSnapshot.self) {
            val subscribeEvent = eventJsonAdapter.toJson(SocketEvent.Subscribe(topicName))
            hostToSocket[topicOwner].send(subscribeEvent)
          } else {
            action.localSubscription.onOpen()
          }

          localSubscribers.put(topicName, action.localSubscription)
        }

        is Action.Publish -> {
          val topicOwner = clusterMapper.topicToHost(clusterSnapshot, action.topic)
          val socketEvent = SocketEvent.Event(action.topic, action.event.toString())
          val eventJson = eventJsonAdapter.toJson(socketEvent)

          if (topicOwner != clusterSnapshot.self) {
            hostToSocket[topicOwner].send(eventJson)
          } else {
            remoteSubscribers.get(action.topic).forEach {
              it.send(eventJson)
            }
          }
        }

        is Action.CancelSubscription -> {
          val topicName = action.localSubscription.topic.name
          val localTopicSubscribers = localSubscribers.get(topicName)
          localTopicSubscribers.remove(action.localSubscription)
          if (localTopicSubscribers.isEmpty()) {
            val topicOwner = clusterMapper.topicToHost(clusterSnapshot, topicName)
            if (topicOwner != clusterSnapshot.self) {
              val unsubscribeEvent = eventJsonAdapter.toJson(SocketEvent.Unsubscribe(topicName))
              hostToSocket[topicOwner].send(unsubscribeEvent)
            }
          }
          action.localSubscription.onClose()
        }
      }
    }
  }

  fun joinCluster() {
    clusterConnector.joinCluster(topicPeer)
  }

  fun leaveCluster() {
    enqueue(Action.LeaveCluster)
  }

  override fun <T : Any> getTopic(name: String): Topic<T> {
    return object : Topic<T> {
      override val name: String
        get() = name

      override fun publish(event: T) {
        enqueue(Action.Publish(name, event))
      }

      override fun subscribe(listener: Listener<T>): Subscription<T> {
        val localSubscription = LocalSubscription(listener, this@RealEventRouter, this)
        enqueue(Action.Subscribe(localSubscription))
        return localSubscription
      }
    }
  }

  internal fun enqueue(action: Action) {
    actionQueue.add(action)
    executor.submit({ drainQueue() })
  }
}

// TODO(tso): Make onOpen, onClose, onEvent async
internal data class LocalSubscription<T : Any>(
  val listener: Listener<T>,
  private val realEventRouter: RealEventRouter,
  override val topic: Topic<T>
) : Subscription<T> {
  fun onOpen() {
    this.listener.onOpen(this)
  }

  fun onClose() {
    this.listener.onClose(this)
  }

  override fun cancel() {
    realEventRouter.enqueue(RealEventRouter.Action.CancelSubscription(this))
  }
}
