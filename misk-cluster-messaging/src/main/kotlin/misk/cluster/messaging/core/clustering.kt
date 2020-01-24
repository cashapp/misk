package misk.cluster.messaging.core

import clustermessaging.ClusterBroadcast
import clustermessaging.ClusterMessagingClient
import clustermessaging.Op
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.ByteString

class ClusterMemberNetwork(
    // this scope has a single threaded context
  private val mailroomScope: CoroutineScope,
  private val self: SelfClusterMember,
  private val remoteClusterMemberPool: ClusterMemberPool,
  private val localTopicRouter: LocalTopicRouter,
  private val clusterMemberDiscoverer: ClusterMemberDiscoverer
) {

  suspend fun registerTopicInterest(topic: String) {
    for (clusterMember in remoteClusterMemberPool.readyMembers) {
      clusterMember.receiveSubscribe(topic)
    }
  }

  suspend fun deregisterTopicInterest(topic: String) {
    for (clusterMember in remoteClusterMemberPool.readyMembers) {
      clusterMember.receiveUnsubscribe(topic)
    }
  }

  suspend fun broadcastMessage(topic: String, outgoingMessage: ByteString) {
    for (clusterMember in remoteClusterMemberPool.readyMembers) {
      clusterMember.receiveMessage(topic, outgoingMessage)
    }
  }

  suspend fun registerRemoteClusterMember(clusterMember: ClusterMember) {
    remoteClusterMemberPool.add(clusterMember, ::setupClusterMember, ::teardownClusterMember)
  }

  fun run() {
    mailroomScope.launch {
      for (clusterMemberSnapshot in clusterMemberDiscoverer.discover()) {
        for (host in clusterMemberSnapshot.hosts) {
          createClusterMember(host)
        }
      }
    }
    mailroomScope.launch {
      while (isActive) {
        val members = remoteClusterMemberPool.readyMembers
        val broadcast = ClusterBroadcast(
            cluster_members = members.map { clustermessaging.ClusterMember(it.host) })
        for (clusterMember in members) {
          clusterMember.broadcastClusterInfo(broadcast)
        }
        delay(5000L)
      }
    }
  }

  private fun createClusterMember(host: String) {
    remoteClusterMemberPool.getOrCreate(host, ::setupClusterMember, ::teardownClusterMember)
  }

  private fun setupClusterMember(clusterMember: ClusterMember) {
    mailroomScope.launch {
      for (topic in localTopicRouter.getAllTopics()) {
        clusterMember.receiveSubscribe(topic)
      }
      for (incomingMessage in clusterMember.messageInbox) {
        localTopicRouter.get(incomingMessage.topic)?.outbox?.send(incomingMessage.payload)
      }
      for (broadcast in clusterMember.clusterBroadcastInbox) {
        for (member in broadcast.cluster_members) {
          createClusterMember(member.host!!)
        }
      }
    }
  }

  private fun teardownClusterMember(clusterMember: ClusterMember) {

  }

}

class ClusterMemberPool(
    // this scope has a single threaded context
  private val mailroomScope: CoroutineScope,
  private val clusterMemberFactory: ClusterMember.Factory
) {
  private val remoteClusterMembers = mutableMapOf<String, ClusterMember>()

  val readyMembers: Collection<ClusterMember>
    get() = remoteClusterMembers.values

  fun run() {
    // todo prune slow members
  }

  fun getOrCreate(
    host: String,
    onCreate: (ClusterMember) -> Unit,
    onDestroy: (ClusterMember) -> Unit
  ): ClusterMember {
    return remoteClusterMembers.getOrPut(host) {
      clusterMemberFactory.create(host).also { add(it, onCreate, onDestroy)}
    }
  }

  fun add(
    clusterMember: ClusterMember,
    onCreate: (ClusterMember) -> Unit,
    onDestroy: (ClusterMember) -> Unit
  ): ClusterMember {
    return clusterMember.also(onCreate)
        .apply {
          outbox.invokeOnClose {
            onDestroy(this)
            remoteClusterMembers.remove(host)
          }
        }
  }

}

class SelfClusterMember(
  val host: String
)

// it is dead if inbox/outbox are closed
class ClusterMember(
  private val self: SelfClusterMember,
  val host: String,
  val messageInbox: ReceiveChannel<ClusterMessage>,
  val clusterBroadcastInbox: ReceiveChannel<ClusterBroadcast>,
  val outbox: SendChannel<Op>
) {

  private val interestedTopics = mutableSetOf<String>()

  suspend fun receiveSubscribe(topic: String) {
    outbox.send(Op(
        sender = self.host,
        subscribe_topic = Op.SubscribeTopic(topic = topic)))
  }

  suspend fun receiveUnsubscribe(topic: String) {
    outbox.send(Op(
        sender = self.host,
        unsubscribe_topic = Op.UnsubscribeTopic(topic = topic)))
  }

  suspend fun receiveMessage(topic: String, outgoingMessage: ByteString) {
    if (isInterestedIn(topic)) {
      outbox.send(Op(
          sender = self.host,
          deliver_message = Op.DeliverMessage(
              topic = topic,
              payload = outgoingMessage)))
    }
  }

  suspend fun broadcastClusterInfo(broadcast: ClusterBroadcast) {
    outbox.send(Op(
        sender = self.host,
        deliver_cluster_broadcast = Op.DeliverClusterBroadcast(
            broadcast = broadcast)))
  }

  private fun isInterestedIn(topic: String): Boolean {
    return interestedTopics.contains(topic)
  }

  class Factory(
    private val mailroomScope: CoroutineScope,
    private val self: SelfClusterMember,
    private val clusterMessagingClient: ClusterMessagingClient
  ) {

    fun create(host: String): ClusterMember {
      val (outbox, inbox) = clusterMessagingClient.ClusterOp().execute()
      return create(host, inbox, outbox)
    }

    fun create(host: String, inbox: ReceiveChannel<Op>, outbox: SendChannel<Op>): ClusterMember {
      val broadcastInbox = inbox.broadcast()

      val messageInbox = mailroomScope.produce {
        for (incomingMessage in broadcastInbox.openSubscription()) {
          incomingMessage.deliver_message?.apply {
            send(ClusterMessage(topic!!, payload!!))
          }
        }
      }

      val clusterBroadcastInbox = mailroomScope.produce {
        for (incomingMessage in broadcastInbox.openSubscription()) {
          incomingMessage.deliver_cluster_broadcast?.apply {
            send(broadcast!!)
          }
        }
      }
      outbox.invokeOnClose {
        messageInbox.cancel()
      }

      val clusterMember = ClusterMember(self, host, messageInbox, clusterBroadcastInbox, outbox)
      mailroomScope.launch {
        for (incomingMessage in broadcastInbox.openSubscription()) {
          incomingMessage.subscribe_topic?.apply {
            clusterMember.interestedTopics.add(topic!!)
          }
          incomingMessage.unsubscribe_topic?.apply {
            clusterMember.interestedTopics.remove(topic!!)
          }
        }
      }
      return clusterMember
    }
  }
}

data class ClusterMessage(val topic: String, val payload: ByteString)

interface ClusterMemberDiscoverer {

  fun discover(): ReceiveChannel<ClusterSnapshot>

  data class ClusterSnapshot(val hosts: List<String>)
}