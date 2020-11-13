package misk.clustering.weights

import com.google.common.util.concurrent.Service

/**
 * Provides the current weight assigned to the cluster.
 *
 * A weight value is between 0 and 100 to indicate how much traffic a cluster should handle.
 * Typically an active-passive setup has 1 active cluster with 100 and 1 passive cluster with 0.
 *
 * If your application does not require dynamic cluster weights, you can install the
 * [ActiveClusterWeightModule]
 *
 * If your application does require dynamic cluster weights, you need must provide your own impl
 * and [ClusterWeightService] for others to depend on.
 */
interface ClusterWeightProvider {

  fun get(): Int
}

/**
 * A marker interface for the Service that produces the [ClusterWeightProvider].
 */
interface ClusterWeightService : Service
