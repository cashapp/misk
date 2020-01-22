package misk.cluster.messaging.core.dispatcher

import misk.cluster.messaging.core.model.ClusterMemberId

interface PeerStore {

  fun isPeerReady(peerId: ClusterMemberId): Boolean

  fun getReadyPeers(): Set<ClusterMemberId>

  fun addPeer(peerId: ClusterMemberId)

  fun removePeer(peerId: ClusterMemberId)

  fun getSelf(): ClusterMemberId


  // peer status: pending, connected, quarantined, disconnected

}