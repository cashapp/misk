package misk.cluster.messaging.core.dispatcher

import misk.cluster.messaging.core.model.ClusterMessage
import misk.cluster.messaging.core.model.SubscriberId
import misk.cluster.messaging.core.model.Topic
import misk.cluster.messaging.core.peer.ClusterMember
import okio.ByteString

sealed class Action

data class OnClusterMemberConnected(val sender: ClusterMember): Action()
data class OnClusterMemberDisconnected(val sender: ClusterMember): Action()
data class OnClusterMessageReceived(val sender: ClusterMember, val message: ClusterMessage): Action()

// todo timer, peer consumption rate

data class OnTopicSubscribed(val topic: Topic, val subscriber: SubscriberId): Action()
data class OnTopicUnsubscribed(val topic: Topic, val subscriber: SubscriberId): Action()
data class OnMessagePublished(val topic: Topic, val content: ByteString, val replySubscriber: SubscriberId): Action()
