package misk.metrics.digester

import com.squareup.digester.protos.service.DigestData

/** TDigest abstract common methods for t-digest implementations */
interface TDigest<T : TDigest<T>> {

  /** Adds an observed value to the digest. */
  fun add(value: Double)

  /**
   * Quantile returns the estimated value at quantile.
   * A given quantile should be in the range of [0, 1.0].
   * If no data has been added then NaN is returned.
   */
  fun quantile(quantile: Double): Double

  /** Returns the count of values added into the digest. */
  fun count(): Long

  /** Returns the sum of all values added into the digest, or NaN if no values have been added. */
  fun sum(): Double

  /**
   * MergeInto merges the data in this digest into the other digest.
   * The other digest is mutated and must be of the same TDigest type.
   */
  fun mergeInto(other: T)

  /** Proto returns a representation of the t-digest that can be later reconstituted into an instance of the same type. */
  fun proto(): DigestData
}
