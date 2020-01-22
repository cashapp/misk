package misk.cluster.messaging.core.internal.dispatcher

import com.google.common.util.concurrent.AbstractExecutionThreadService
import com.google.inject.Inject
import com.google.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import misk.cluster.messaging.core.dispatcher.Action
import misk.cluster.messaging.core.dispatcher.Dispatcher
import misk.cluster.messaging.core.dispatcher.OnClusterMemberConnected
import misk.cluster.messaging.core.dispatcher.OnClusterMemberDisconnected
import misk.cluster.messaging.core.dispatcher.OnClusterMessageReceived
import misk.cluster.messaging.core.dispatcher.PeerStore
import misk.cluster.messaging.core.dispatcher.SendClusterMessage
import misk.cluster.messaging.core.dispatcher.SubscriptionStore
import misk.cluster.messaging.core.model.ClusterMemberId
import misk.cluster.messaging.core.model.DeliverClusterBroadcast
import misk.cluster.messaging.core.model.DeliverMessage
import misk.cluster.messaging.core.model.ClusterMessage
import misk.cluster.messaging.core.model.SubscribeTopic
import misk.cluster.messaging.core.model.UnsubscribeTopic

// todo have peers talk to this object
// todo this object should call methods on peers
@Singleton
@Suppress("UnstableApiUsage")
internal class RealDispatcher @Inject constructor(
  private val peerStore: PeerStore,
  private val subscriptionStore: SubscriptionStore
) : AbstractExecutionThreadService(), Dispatcher {

  private val inbox = Channel<Action>()

  override suspend fun send(action: Action) {
    inbox.send(action)
  }

  override fun run() {
    runBlocking {
      withContext(Dispatchers.Main) {
        for (action in inbox) {
          handleAction(action)
        }
      }
    }
  }

  private suspend fun handleAction(action: Action) {
    when (action) {
      is OnClusterMemberConnected -> {
        peerStore.addPeer(action.sender)
        // peer lifecycle: discovered, connecting, connected,
        // peer status: pending, connected, quarantined, disconnected
        // todo connect to peer, retry
        // if peer is cluster member, send all local subscriptions
      }
      is OnClusterMemberDisconnected -> {
        peerStore.removePeer(action.sender)
      }
      is OnClusterMessageReceived -> {
        if (!peerStore.isPeerReady(action.sender)) {
          return
        }
        handleClusterMemberMessage(action.sender, action.message)
      }
      // timer -> broadcast
    }
  }

  private suspend fun handleClusterMemberMessage(sender: ClusterMemberId, message: ClusterMessage) {
    when (message) {
      is SubscribeTopic -> {
        subscriptionStore.addSubscription(sender, message.topic)
      }
      is UnsubscribeTopic -> {
        subscriptionStore.removeSubscription(sender, message.topic)
      }
      is DeliverMessage -> {
        val peers = subscriptionStore.getSubscribers(message.topic)
        for (peer in peers) {
          if (peer is LeafNodeId) {
            // todo disconnect peer if it is slow
            outbox.send(SendClusterMessage(peer, message))
          }
        }
      }
      is DeliverClusterBroadcast -> {
        // if there is a new member,
      }
    }
  }


//  private suspend fun handleLeafNodeMessage(sender: PeerId, message: ClusterMessage) {
//    when (message) {
//      is SubscribeTopic -> {
//        subscriptionStore.addSubscription(sender, message.topic)
//        for (peer in peerStore.getReadyPeers()) {
//          if (peer is ClusterMemberId) {
//            outbox.send(SendPeerMessage(peerStore.getSelf(), message))
//          }
//        }
//      }
//      is UnsubscribeTopic -> {
//        subscriptionStore.removeSubscription(sender, message.topic)
//        // todo if no other peers are subscribed to this topic
//        for (peer in peerStore.getReadyPeers()) {
//          if (peer is ClusterMemberId) {
//            outbox.send(SendPeerMessage(peerStore.getSelf(), message))
//          }
//        }
//      }
//      is DeliverMessage -> {
//        val peers = subscriptionStore.getSubscribers(message.topic)
//        for (peer in peers) {
//          if (peer is ClusterMemberId) {
//            // todo disconnect peer if it is slow
//            outbox.send(SendPeerMessage(peer, message))
//          }
//        }
//      }
////      is DeliverClusterBroadcast -> {
////        // if there is a new member,
////      }
//    }
//  }

}