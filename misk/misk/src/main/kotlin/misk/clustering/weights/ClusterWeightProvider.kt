package misk.clustering.weights

/**
 * Provides the current weight assigned to the cluster.
 *
 * A weight value is between 0 and 100 to indicate how much traffic a cluster should handle.
 * Typically an active-passive setup has 1 active cluster with 100 and 1 passive cluster with 0.
 *
 * If your application does not require dynamic cluster weights, you can use the static
 * [ActiveClusterWeight].
 */
interface ClusterWeightProvider {

  fun get(): Int
}
