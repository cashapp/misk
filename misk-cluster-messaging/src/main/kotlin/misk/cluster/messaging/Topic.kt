package misk.cluster.messaging

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

// Core APIs
// TODO need to differentiate cluster member and remote listener?, cluster member are routers,

data class Topic(val name: String)

data class Message(val content: String) // reply address?

data class Subscription(val topic: Topic, val subscriber: String)

data class ClusterBroadcast(val clusterMembers: List<ClusterMemberSnapshot>)

sealed class PeerSnapshot

/**
 * A cluster member broadcasts subscription list and cluster topology in the cluster.
 */
data class ClusterMemberSnapshot(val id: String, val host: String): PeerSnapshot()

// this is likely a local thread or a grpc/ws connection
data class LeafNodeSnapshot(val id: String): PeerSnapshot()


// Peer factory? Peer

// stateless

//data class PeerReference(val )

interface SubscriptionStore {

  fun addSubscription(topic: Topic, peerSnapshot: PeerSnapshot)

  fun removeSubscription(topic: Topic, peerSnapshot: PeerSnapshot)

  fun getSubscribers(topic: Topic): List<PeerSnapshot>

}

class Coordinator {

  // rate on each queue

  // new message

  //

  // handle message
  fun handleAction() {

  }
}




// Actions

sealed class Action()

data class ReceiveSubscribeTopic(val sender: PeerSnapshot, val topic: Topic)

data class UnsubscribeTopic(val sender: PeerSnapshot, val topic: Topic)

data class ReceiveMessage(val sender: PeerSnapshot, val topic: Topic, val message: Message)

data class ReceiveClusterBroadcast(val sender: ClusterMemberSnapshot, val clusterBroadcast: ClusterBroadcast)


// Side Effects

sealed class SideEffect()

data class SendMessage(val sender: PeerSnapshot, val topic: Topic, val message: Message)

class ConnectClusterMember
class DisconnectClusterMember

// ...

// ================ High Level ================

// ================ Stateful ================

// Actor coroutines ?


// stateful and has lifecycle
interface Peer {

  val inbox: Inbox
  val outbox: Outbox
}

interface Lifecycle {

}

interface Inbox {

  suspend fun send()
}

interface Outbox {

  suspend fun receive()
}



// Domain coordination
interface CoroutinePeer<S: Any, R: Any> {

  val sendChannel: SendChannel<S>
  val receiveChannel: ReceiveChannel<R>
}


// implementation details

// messages will be boardcast to all
class ClusterMemberPeer {

}

// messages will be boardcast to all
class LocalPeer {

}

