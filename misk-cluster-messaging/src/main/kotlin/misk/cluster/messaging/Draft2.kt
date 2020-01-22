package misk.cluster.messaging

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.filter
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import okio.ByteString

interface MessageRouter {
  val messageQueuePool: MessageQueuePool

  fun publish(topic: Topic, content: ByteString) {
    runBlocking {
      // try reusing local queues
      LocalMessageQueue(topic).inbox.send(
          Message(topic, content))
    }
  }

  fun subscribe(topic: Topic): Flow<ByteString> {
    return runBlocking {
      // timeout?
      // when to remove subscription?
      LocalMessageQueue(topic).outbox.consumeAsFlow().map { it.content }
    }
  }
}

data class Topic(val name: String)

data class Message(val topic: Topic, val content: ByteString)

data class MessageQueueId(val id: String)

interface MessageQueue {
  val id: MessageQueueId
  val inbox: ReceiveChannel<Message>
  val outbox: SendChannel<Message>
}

data class SubscriptionRequest(val topic: Topic)

// add, remove
interface TopicSubscribingMessageQueue : MessageQueue {

  val subscriptionRequestInbox: ReceiveChannel<SubscriptionRequest>
}

interface TopicPublishingMessageQueue : MessageQueue {

  val subscriptionRequestOutbox: SendChannel<SubscriptionRequest>
}

enum class DesiredState {
  Connected,
  Quarantined,
  Disconnected
}

interface LifecycleAwareMessageQueue : MessageQueue {

  val setDesiredStateOutbox: Channel<DesiredState>
}

private val brainContext = newSingleThreadContext("Brain Context")

class MessageQueuePool {
  lateinit var messageQueues: Map<MessageQueueId, MessageQueue>

  fun getMessageQueue(id: MessageQueueId): MessageQueue? {
    return messageQueues[id]
  }

  fun getMessageQueues(): Collection<MessageQueue> {
    return messageQueues.values
  }

  fun start() {
    // subscribes to cluster broadcast
    // subscribes to k8s watcher
    // maintains connections to cluster members

    // synchronously create local queue?

  }

}

class InterestGraph {

  lateinit var messageQueuePool: MessageQueuePool

  fun getSubscribers(topic: Topic): Set<MessageQueueId> {
    return emptySet()
  }

  private var job: Job? = null

  fun run() {
    runBlocking {
      job = launch {
        for (sender in messageQueuePool.getMessageQueues()) {
          if (sender is TopicSubscribingMessageQueue && sender !is TopicPublishingMessageQueue) {
            launch {
              withContext(brainContext) {
                for (request in sender.subscriptionRequestInbox) {
                  // forward requests to topic publishers
                  publishInterest(request)
                }
              }
            }
          }
        }
      }
    }
  }

  fun stop() {
    job?.cancel()
    job = null
  }

  // if new
  private suspend fun consumeSubscriptionRequests() {

  }

  // if dead, unpublish interest

  private suspend fun publishInterest(request: SubscriptionRequest) {
    for (publisher in messageQueuePool.getMessageQueues()) {
      if (publisher !is TopicPublishingMessageQueue) {
        continue
      }
      publisher.subscriptionRequestOutbox.send(request)
    }
  }
}

class MessageDispatcher {

  // subscribes to cluster member messages and writes to clients

  // read only api

  lateinit var messageQueuePool: MessageQueuePool
  lateinit var interestGraph: InterestGraph

  private var job: Job? = null

  fun run() {
    runBlocking {
      job = launch {
        for (sender in messageQueuePool.getMessageQueues()) {
          launch {
            withContext(brainContext) {
              for (message in sender.inbox) {
                forwardMessage(sender, message)
              }
            }
          }
        }
      }
    }
  }

  fun stop() {
    job?.cancel()
    job = null
  }

  private suspend fun forwardMessage(sender: MessageQueue, message: Message) {
    val subscribers = interestGraph.getSubscribers(message.topic)
    for (subscriber in subscribers) {
      val queue = messageQueuePool.getMessageQueue(subscriber) ?: continue
      if (queue.outbox.isClosedForSend) {
        // todo disconnect peer if it is slow
        continue
      }
      queue.outbox.send(message)
    }
  }

}

class LocalMessageQueue(
  val topic: Topic
) : TopicSubscribingMessageQueue {
  override val id: MessageQueueId =
      MessageQueueId("topic-$topic")
  override val inbox: Channel<Message> = Channel()
  override val outbox: Channel<Message> = Channel(5)
  override val subscriptionRequestInbox: Channel<SubscriptionRequest> = Channel() // consider flow?
}

// represents a connected client
class ClusterMember(
  private val snapshot: ClusterMemberSnapshot,
  private val sendChannel: SendChannel<ClusterOp>,
  private val receiveChannel: ReceiveChannel<ClusterOp>
) : TopicSubscribingMessageQueue,
    TopicPublishingMessageQueue,
    LifecycleAwareMessageQueue {

  override val id: MessageQueueId =
      MessageQueueId(
          "cluster-member-${snapshot.host}")
  override val subscriptionRequestInbox: Channel<SubscriptionRequest>
    get() = TODO(
        "not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val subscriptionRequestOutbox: Channel<SubscriptionRequest>
    get() = TODO(
        "not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val setDesiredStateOutbox: Channel<DesiredState>
    get() = TODO(
        "not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val inbox: ReceiveChannel<Message> = receiveChannel.filter { it is DeliverMessage }.map {
    val t = it as DeliverMessage
    Message(t.topic, t.content)
  }
  override val outbox: Channel<Message> = Channel(5)

  fun join() {
    runBlocking {
      var lastDesiredState: DesiredState =
          DesiredState.Connected


      launch {
        for (desiredState in setDesiredStateOutbox) {
          if (desiredState == lastDesiredState) {
            continue
          }
          when (lastDesiredState to desiredState) {
            DesiredState.Connected to DesiredState.Disconnected -> {
              //
            }
            DesiredState.Disconnected to DesiredState.Connected -> {
              //
            }
          }
        }
      }

      launch {

      }
    }
  }

  private fun connect(): Pair<SendChannel<ClusterOp>, ReceiveChannel<ClusterOp>> {
    TODO()
  }

  private fun sendClusterOp(clusterOp: ClusterOp) {

  }
}

sealed class ClusterMemberState {
  object Disconnected
  object Connecting
  data class Connected(
    val sendChannel: SendChannel<ClusterOp>,
    val receiveChannel: ReceiveChannel<ClusterOp>
  )

  object Disconnecting
}

interface ClusterOp

data class SubscribeTopic(val topic: Topic) :
    ClusterOp
data class UnsubscribeTopic(val topic: Topic) :
    ClusterOp
data class DeliverMessage(val topic: Topic, val content: ByteString) :
    ClusterOp
data class DeliverClusterBroadcast(val clusterBroadcast: ClusterBroadcast) :
    ClusterOp

data class ClusterMemberSnapshot(val host: String)
data class ClusterBroadcast(val clusterMembers: List<ClusterMemberSnapshot>)
