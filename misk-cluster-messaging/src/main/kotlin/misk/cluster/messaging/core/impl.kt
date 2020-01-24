package misk.cluster.messaging.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.launch
import okio.ByteString

// mailroom is a object in a single threaded coroutine scope

// mailboxes are in caller scopes

class RealMailbox(
  private val scopedTransport: BroadcastChannel<ByteString>
) : Mailbox {

  override fun open(): Pair<SendChannel<ByteString>, ReceiveChannel<ByteString>> {
    return scopedTransport to scopedTransport.openSubscription()
  }

  override fun close() {
    scopedTransport.close()
  }

  class Factory {

    fun create(
      mailboxScope: CoroutineScope,
      localTopic: LocalTopic
    ): Mailbox {
      val scopedTransport: BroadcastChannel<ByteString> = mailboxScope.broadcast {
        for (outgoingMessage in localTopic.outbox.openSubscription()) {
          send(outgoingMessage)
        }
      }
      mailboxScope.launch {
        for (incomingMessage in scopedTransport.openSubscription()) {
          localTopic.inbox.send(incomingMessage)
        }
      }
      return RealMailbox(scopedTransport)
    }
  }
}

class RealMailroom(
    // this scope has a single threaded context
  private val mailroomScope: CoroutineScope,
  private val mailboxFactory: RealMailbox.Factory,
  private val localTopicRouter: LocalTopicRouter,
  private val clusterMemberNetwork: ClusterMemberNetwork
) : Mailroom {

  override fun openMailbox(mailboxScope: CoroutineScope, topic: String): Mailbox {
    val transport = localTopicRouter.getOrCreate(topic, ::setupTransport, ::teardownTransport)
    return mailboxFactory.create(mailboxScope, transport)
  }

  // A bunch of tricky local state
  //  - we could have 2 local subscribers
  //  - we could have 2 local publishers
  // as far as the network is concerned, these need to be deduplicated

  // on subscribe
  //   1. create channels
  //   2. tell mailroom about these channels asynchronously
  //   3. (on mailroom scope): register the subscription locally and with peers

  private fun setupTransport(localTopic: LocalTopic) {
    mailroomScope.launch {
      clusterMemberNetwork.registerTopicInterest(localTopic.topic)
      for (outgoingMessage in localTopic.inbox.openSubscription()) {
        clusterMemberNetwork.broadcastMessage(localTopic.topic, outgoingMessage)
      }
    }
  }

  private fun teardownTransport(localTopic: LocalTopic) {
    mailroomScope.launch {
      clusterMemberNetwork.deregisterTopicInterest(localTopic.topic)
    }
  }
}

// We can constrain the number of local topics to exactly 1 for a given string

// challenge : subscribe comes in from the network, and this races a local topic publisher in the router

// local:   create LocalTopic to publish
// network: inbound subscription creation

// server B queries its local topic for C_123
// server B subscribes to topic C_123

// make this an message


// server A publishes event 1 on topic C_123
// sever A finds out about B's subscription
// B has lost event 1

class LocalTopicRouter(
    // this scope has a single threaded context
  private val mailroomScope: CoroutineScope,
  private val localTopicFactory: LocalTopic.Factory
) {
  private val transports = mutableMapOf<String, LocalTopic>()

  fun getAllTopics(): Set<String> {
    return transports.keys
  }

  fun get(topic: String): LocalTopic? {
    return transports[topic]
  }

  // probably synchronize using concurrent hash map
  fun getOrCreate(
    topic: String,
    onCreate: (LocalTopic) -> Unit,
    onDestroy: (LocalTopic) -> Unit
  ): LocalTopic {
    return transports.getOrPut(topic) {
      localTopicFactory.create(topic)
          .also(onCreate)
          .apply {
            outbox.invokeOnClose {
              onDestroy(this)
              transports.remove(topic)
            }
          }
    }
  }

  fun run() {
    // todo: prune idle transports
  }

}
// 100K active topics in the cluster, delta rate of 1000/second

// cluster member A: connected to the app

// cluster member B: writing to the topic

// cluster member C..Z: writing to the topic

/**
 * A topic with one or more publishers or subscribers in the current process. This is the bridge
 * between the internal implementation and in-process consumers and subscribers.
 */
class LocalTopic(
  val topic: String,
  val inbox: BroadcastChannel<ByteString>,
  val outbox: BroadcastChannel<ByteString>
) {

  init {
    // when inbox and out box are idle, close the channel...
  }

  // 1, 2, 3, 4, 5, 4, 3, 3,,3,3,3,3,3,3
  // A    s3  4  5
  // B         s 5
  class Factory(
    private val mailroomScope: CoroutineScope
      ) {


    fun create(topic: String): LocalTopic {
      return LocalTopic(topic, BroadcastChannel(64), BroadcastChannel(64))
          .let { localTopic ->
            mailroomScope.launch {
              for (incomingMessage in localTopic.inbox.openSubscription()) {
                localTopic.outbox.send(incomingMessage)
              }
            }
          }
    }
  }
}

