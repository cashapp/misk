package misk.cluster.messaging.core2

import com.google.common.collect.LinkedHashMultimap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.ByteString

data class ClusterMemberSnapshot(val host: String)
data class ClusterBroadcast(val clusterMembers: List<ClusterMemberSnapshot>)

sealed class ClusterOp {
  data class SubscribeTopic(val topic: String) : ClusterOp()
  data class UnsubscribeTopic(val topic: String) : ClusterOp()
  data class DeliverMessage(val topic: String, val content: ByteString) : ClusterOp()
  data class DeliverClusterBroadcast(val clusterBroadcast: ClusterBroadcast) : ClusterOp()
}

data class ClusterOpInboxAddress(
  val clusterMemberName: String
) : Address<ClusterOp> {
  override val stringValue: String
    get() = clusterMemberName
  override val messageType = ClusterOp::class
}

data class ClusterOpOutboxAddress(
  val clusterMemberName: String
) : Address<ClusterOp> {
  override val stringValue: String
    get() = clusterMemberName
  override val messageType = ClusterOp::class
}

class ClusterDiscoveryOperator {
  // cluster members...
  // suspend read api


}

class ClusterMessageDispatcher(
  private val coroutineScope: CoroutineScope,
  private val mailboxRegistry: MailboxRegistry
) {
  init {
    coroutineScope.launch {
      receiveMessage()
    }
    coroutineScope.launch {
      sendInterest()
    }
  }

  // todo rename
  private suspend fun receiveMessage() {
    for (envelope in mailboxRegistry.fanIn(ClusterOpInboxAddress::class)) {
      when (val op = envelope.message) {
        is ClusterOp.DeliverMessage -> {
          val rawMessageInbox = mailboxRegistry.getMailbox(TopicRawMessageInboxAddress(op.topic))
          rawMessageInbox.send(op.content)
        }
      }
    }
  }

  private suspend fun sendInterest() {
    for (envelope in mailboxRegistry.fanIn(TopicRawMessageOutboxAddress::class)) {
      val topicName = envelope.address.topicName
      val interestedClusterMembers = topicToClusterMember.get(topicName)
      for (clusterMember in interestedClusterMembers) {
        val clusterMemberOpOutbox =
            mailboxRegistry.getMailbox(ClusterOpOutboxAddress(clusterMember))
        clusterMemberOpOutbox.send(ClusterOp.DeliverMessage(topicName, envelope.message))
      }
    }
  }
}

// stateful operator... state should be somewhere else?
class RemoteInterestOperator(
  private val coroutineScope: CoroutineScope,
  private val mailboxRegistry: MailboxRegistry
) {

  private val topicToClusterMember = LinkedHashMultimap.create<String, String>()

  init {
    coroutineScope.launch {
      receiveInterest()
    }
  }

  // todo rename
  private suspend fun receiveInterest() {
    for (envelope in mailboxRegistry.fanIn(ClusterOpInboxAddress::class)) {
      when (val op = envelope.message) {
        is ClusterOp.SubscribeTopic -> {
          topicToClusterMember.put(op.topic, envelope.address.clusterMemberName)
        }
        is ClusterOp.UnsubscribeTopic -> {
          topicToClusterMember.remove(op.topic, envelope.address.clusterMemberName)
        }
      }
    }
  }

  // receive request and publish response.

}

class LocalInterestOperator(
  private val coroutineScope: CoroutineScope,
  private val mailboxRegistry: MailboxRegistry
) {

  init {
    coroutineScope.launch {
      mailboxRegistry.fanIn(TopicMessageAddress::class)

      // we want to subscribe to a type of address and
      val mailboxUpdate = mailboxRegistry.getMailbox(MailboxUpdateAddress)
          .transport
          .openSubscription()
      for (mailbox in mailboxUpdate) {
        when (mailbox.message) {
          is MailboxUpdate.Created -> {
            if (mailbox.message.address is TopicMessageAddress) {
              launch {
                registerInterest(mailbox.message.address.topicName)
              }
            }
            if (mailbox.message.address is TopicRawMessageOutboxAddress) {
              launch {
                startForwardingRawMessageOutbox(mailbox.message.address)
              }
            }
          }
          is MailboxUpdate.Evicted -> {
            if (mailbox.message.address is TopicMessageAddress) {
              launch {
                deregisterInterest(mailbox.message.address.topicName)
              }
            }
          }
        }
      }
    }
  }

  private suspend fun registerInterest(topic: String) {
    mailboxRegistry.fanOut(ClusterOpOutboxAddress::class, ClusterOp.SubscribeTopic(topic))
  }

  private suspend fun deregisterInterest(topic: String) {
    mailboxRegistry.fanOut(ClusterOpOutboxAddress::class, ClusterOp.UnsubscribeTopic(topic))
  }

  // resend all interests...
  private suspend fun sendInterest() {
    for (envelope in mailboxRegistry.fanIn(MailboxUpdateAddress::class)) {
      if (envelope.message is MailboxUpdate.Created) {
        if (envelope.message.address is ClusterOpOutboxAddress) {
          // send all interests...
          mailboxRegistry.getMailbox(envelope.message.address)
              .send(ClusterOp.SubscribeTopic("all topics..."))
        }
      }
    }
  }

}



