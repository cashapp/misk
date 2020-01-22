package misk.cluster.messaging.core.peer

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import misk.cluster.messaging.core.model.ClusterMessage

// stateful and has lifecycle
interface Peer {
  val inbox: SendChannel<ClusterMessage>
  val outbox: ReceiveChannel<ClusterMessage>

}


// on server: self, incoming app requests/local subscriptions

// on device: self
interface LocalPeer : Peer {

}



// on server: other cluster members

// on device: the server
interface RemotePeer : Peer {

  suspend fun join(request: JoinRequest): JoinResponse

  suspend fun leave(request: LeaveRequest): LeaveResponse

  interface JoinRequest

  interface JoinResponse {
    // message channel
  }

  interface LeaveRequest

  interface LeaveResponse

}

interface PeerNetwork {

  fun getSelf(): Peer

  fun getOrCreate(): Peer
}

interface PeerJoinRequest {

}

interface PeerJoinResponse {

}

/**
 * A cluster member broadcasts subscription list and cluster topology in the cluster.
 */
interface ClusterMember : Peer{
  // host
}

interface LeafNode: Peer {
  // address...
}
