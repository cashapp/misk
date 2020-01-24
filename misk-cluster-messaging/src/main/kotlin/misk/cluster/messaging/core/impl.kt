package misk.cluster.messaging.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.ByteString

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
      transport: Transport
    ): Mailbox {
      val scopedTransport: BroadcastChannel<ByteString> = mailboxScope.broadcast {
        for (outgoingMessage in transport.outbox.openSubscription()) {
          send(outgoingMessage)
        }
      }
      mailboxScope.launch {
        for (incomingMessage in scopedTransport.openSubscription()) {
          transport.inbox.send(incomingMessage)
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
  private val transportPool: TransportPool,
  private val clusterMemberNetwork: ClusterMemberNetwork
) : Mailroom {

  override fun openMailbox(mailboxScope: CoroutineScope, topic: String): Mailbox {
    val transport = transportPool.getOrCreate(topic, ::setupTransport, ::teardownTransport)
    return mailboxFactory.create(mailboxScope, transport)
  }

  private fun setupTransport(transport: Transport) {
    mailroomScope.launch {
      clusterMemberNetwork.registerTopicInterest(transport.topic)
      for (outgoingMessage in transport.inbox.openSubscription()) {
        clusterMemberNetwork.broadcastMessage(transport.topic, outgoingMessage)
      }
    }
  }

  private fun teardownTransport(transport: Transport) {
    mailroomScope.launch {
      clusterMemberNetwork.deregisterTopicInterest(transport.topic)
    }
  }
}

class TransportPool(
    // this scope has a single threaded context
  private val mailroomScope: CoroutineScope,
  private val transportFactory: Transport.Factory
) {
  private val transports = mutableMapOf<String, Transport>()

  fun getAllTopics(): Set<String> {
    return transports.keys
  }

  fun get(topic: String): Transport? {
    return transports[topic]
  }

  // probably synchronize using concurrent hash map
  fun getOrCreate(
    topic: String,
    onCreate: (Transport) -> Unit,
    onDestroy: (Transport) -> Unit
  ): Transport {
    return transports.getOrPut(topic) {
      transportFactory.create(topic)
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

class Transport(
  val topic: String,
  val inbox: BroadcastChannel<ByteString>,
  val outbox: BroadcastChannel<ByteString>
) {

  init {
    // when inbox and out box are idle, close the channel...
  }

  class Factory {

    fun create(topic: String): Transport {
      return Transport(topic, BroadcastChannel(64), BroadcastChannel(64))
    }
  }
}

