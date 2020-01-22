package misk.cluster.messaging.core.dispatcher

import misk.cluster.messaging.core.model.ClusterMemberId
import misk.cluster.messaging.core.model.SubscriberId
import misk.cluster.messaging.core.model.Topic

/**
 * In memory data structure.
 */
interface SubscriptionStore {

  fun getRemoteSubscribers(topic: Topic): Set<ClusterMemberId>

  fun getLocalSubscribers(topic: Topic): Set<SubscriberId>

  fun addSubscriber(topic: Topic, clusterMemberId: ClusterMemberId)

  fun addSubscriber(topic: Topic, subscriberId: SubscriberId)

}