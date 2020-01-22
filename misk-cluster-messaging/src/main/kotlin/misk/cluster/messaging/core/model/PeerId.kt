package misk.cluster.messaging.core.model


// this is likely a local thread or a grpc/ws connection
data class SubscriberId(val id: String)

class ClutsterMemberSnapshot()

sealed class PeerRole {
  object Router
  object LeafNode
}


sealed class PeerState {

  object Discovered

  object Connecting

  object Connected

  object Disconnecting

  object WaitingForRetry

  object Disconnected
}


/*


on server
we have
- a self cluster member
- n remote cluster members
- m remote leaf nodes

- a cluster member discoverer
- a leaf node discoverer

on mobile
we have
- a self leaf node
- a remote ??? peer

- a ?? discoverer

*/
