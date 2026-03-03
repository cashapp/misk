package misk.metrics.digester

import com.squareup.digester.protos.service.DigestData
import com.squareup.digester.protos.tdigest.MergingDigestData
import okio.ByteString.Companion.toByteString

/**
 * Wraps an adapted t-digest implementation from Stripe's Veneur project
 */
class VeneurDigest : TDigest<VeneurDigest> {

  private val mergingDigest: MergingDigest
  private var count: Long = 0
  private var sum: Double = 0.0

  /** Creates a TDigest backed by a VeneurDigest, using a default compression level */
  constructor() {
    mergingDigest = MergingDigest(50.0)
  }

  /**
   * Creates a VeneurDigest from a DigestData proto
   * The DigestData proto must have veneur_digest set correctly
   */
  constructor (digestData: DigestData) {
    val mergingDigestData: MergingDigestData =
        MergingDigestData.ADAPTER.decode(digestData.veneur_digest)

    mergingDigest = MergingDigest(mergingDigestData)
    count = digestData.count
    sum = digestData.sum
  }

  /** Adds a new observation to the t-digest */
  override fun add(value: Double) {
    mergingDigest.add(value, 1.0)
    count += 1
    sum += value
  }

  /**
   * Returns a value such that the fraction of values in td below that value is
   * approximately equal to quantile. Returns NaN if the digest is empty.
   */
  override fun quantile(quantile: Double): Double {
    return mergingDigest.quantile(quantile)
  }

  /** Returns the mergingDigest instance */
  fun mergingDigest(): MergingDigest {
    return mergingDigest
  }

  /** Returns the count of the number of observations recorded within the t-digest */
  override fun count(): Long {
    return count
  }

  /** Returns the sum of all values added into the digest, or NaN if no values have been added */
  override fun sum(): Double {
    if (count > 0) {
      return sum
    }
    return Double.NaN
  }

  /** Merges this t-digest into another t-digest */
  override fun mergeInto(other: VeneurDigest) {
    other.mergingDigest.mergeFrom(mergingDigest)
    other.count += count
    other.sum += sum
  }

  /** Returns a representation fo the t-digest that can be later be reconstituted into an instance of the same type */
  override fun proto(): DigestData {
    val encode: ByteArray = MergingDigestData.ADAPTER.encode(mergingDigest.data())
    return DigestData(count, sum, encode.toByteString())
  }
}
