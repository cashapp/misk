package misk.cluster.messaging.core.internal.dispatcher

import misk.cluster.messaging.core.model.Topic

internal data class SubscriptionSnapshot(val topic: Topic, val subscriber: String)