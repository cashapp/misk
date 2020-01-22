package misk.cluster.messaging.core.model

import okio.ByteString

interface ClusterMessage
interface ClientMessage

data class SubscribeTopic(val topic: Topic): ClusterMessage, ClientMessage
data class UnsubscribeTopic(val topic: Topic): ClusterMessage, ClientMessage
data class DeliverMessage(val topic: Topic, val content: ByteString): ClusterMessage, ClientMessage
data class DeliverClusterBroadcast(val clusterBroadcast: ClusterBroadcast): ClusterMessage

data class ClusterBroadcast(val clusterMembers: List<ClutsterMemberSnapshot>)