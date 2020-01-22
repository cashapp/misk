package misk.cluster.messaging

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import misk.cluster.messaging.core.dispatcher.Action

// Core APIs
// TODO need to differentiate cluster member and remote listener?, cluster member are routers,

// Peer factory? Peer

// stateless

//data class PeerReference(val )

class Coordinator {

  // rate on each queue

  // new message

  //

  // handle message
  fun handleAction(action: Action) {

  }
}




// Actions

// Side Effects

// ...

// ================ High Level ================

// ================ Stateful ================

// Actor coroutines ?



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

