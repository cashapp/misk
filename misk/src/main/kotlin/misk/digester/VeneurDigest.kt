package misk.digester

import misk.proto.service.DigestData
import misk.proto.tdigest.MergingDigestData
import okio.ByteString.Companion.toByteString

class VeneurDigest {

  /** VeneurDigest wraps an adapted t-digest implementation from Stripe's Veneur project */
  var mergingDigest: MergingDigest = MergingDigest()
  var count: Long = 0
  var sum: Double = 0.0

  constructor()

  constructor(mergingDigest: MergingDigest, count: Long, sum: Double) {
    this.mergingDigest = mergingDigest
    this.count = count
    this.sum = sum
  }

  /** add adds a new observation to the t-digest */
  fun add(value: Double) {
    mergingDigest.add(value, 1.0)
    count += 1
    sum += value
  }

  /** count returns the count of the number of observations recorded within the t-digest */
  fun count(): Long {
    return count
  }

  /** sum returns the sum of all values added into the digest, or NaN if no values have been added */
  fun sum(): Double {
    if (count > 0) {
      return sum
    }
    return Double.NaN
  }

  /**merges this t-digest into another t-digest */
  fun mergeInto(other: VeneurDigest) {
    other.mergingDigest.merge(mergingDigest)
    other.count += count
    other.sum += sum
  }

  /**proto returns a representation fo the t-digest that can be later be reconstituted into an instance of the same type */
  fun proto(): DigestData {
    val encode: ByteArray = MergingDigestData.ADAPTER.encode(mergingDigest.data())
    return DigestData(count, sum, encode.toByteString())
  }

  /** NewVeneurDigest creates a TDigest backed by a VeneurDigest */
  fun newVeneurDigest(compression: Double) : VeneurDigest {
    return VeneurDigest(mergingDigest.newMerging(compression), 0, 0.0)
  }

  /** NewDefaultVeneurDigest creates a TDigest backed by a VeneurDigest, using a default compression level */
  fun newDefaultVeneurDigest(): VeneurDigest {
    return VeneurDigest(mergingDigest.newMerging(50.0), 0, 0.0)
  }

  /**
   *  NewVeneurDigestFromProto creates a VeneurDigest from a DigestData proto
   *  The DigestData proto must have veneur_digest set correctly
   */
  fun newVeneurDigestFromProto(digestData: DigestData): VeneurDigest {
    var mergingDigestData: MergingDigestData = MergingDigestData.ADAPTER.decode(digestData.veneur_digest)

    return VeneurDigest(
        mergingDigest.newMergingFromData(mergingDigestData),
        digestData.count,
        digestData.sum
    )

  }
}