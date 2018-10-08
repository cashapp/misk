package misk.digester

import misk.metrics.Histogram
import misk.proto.service.DigestData
import misk.proto.tdigest.MergingDigestData
import okio.ByteString.Companion.toByteString

class VeneurDigest {
  /*constructor (
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: DoubleArray?
  ): Histogram {*/

  /** VeneurDigest wraps an adapted t-digest implementation from Stripe's Veneur project */
  interface VeneurDigestStruct {
    var mergingDigest: MergingDigest.MergingDigestStruct
    var count: Long
    var sum: Double
  }

  var mergingDigestInstance = MergingDigest() //this should not be instantiated here?

  /** add adds a new observation to the t-digest */
  fun add(v: VeneurDigestStruct, value: Double) {
    mergingDigestInstance.add(v.mergingDigest, value, 1.0)
    v.count += 1
    v.sum += value
  }

  /** count returns the count of the number of observations recorded within the t-digest */
  fun count(v: VeneurDigestStruct): Long {
    return v.count
  }

  /** sum returns the sum of all values added into the digest, or NaN if no values have been added */
  fun sum(v: VeneurDigestStruct): Double {
    if (v.count > 0) {
      return v.sum
    }
    return Double.NaN
  }

  /**merges this t-digest into another t-digest */
  fun mergeInto(v: VeneurDigestStruct, other: VeneurDigestStruct) {
    mergingDigestInstance.merge(other.mergingDigest, v.mergingDigest)
    other.count += v.count
    other.sum += v.sum
  }

  /**proto returns a representation fo the t-digest that can be later be reconstituted into an instance of the same type */
  fun proto(v: VeneurDigestStruct): DigestData {
    //encode example
    val encode: ByteArray = MergingDigestData.ADAPTER.encode(mergingDigestInstance.data(v.mergingDigest))
    return DigestData(v.count, v.sum, encode.toByteString())

    //decode example
    //var decode: MergingDigestData = MergingDigestData.ADAPTER.decode(encode)

  }

  /** NewVeneurDigest creates a TDigest backed by a VeneurDigest */
  fun newVeneurDigest(compression: Double) : VeneurDigestStruct {
   return object : VeneurDigestStruct {
        override var mergingDigest = mergingDigestInstance.newMerging(compression)
        override var count: Long = 0
        override var sum = 0.0
    }
  }

  /** NewDefaultVeneurDigest creates a TDigest backed by a VeneurDigest, using a default compression level */
  fun newDefaultVeneurDigest(): VeneurDigestStruct {
    return object : VeneurDigestStruct {
      override var mergingDigest = mergingDigestInstance.newMerging(50.0)
      override var count: Long = 0
      override var sum = 0.0
    }
  }

  /**
   *  NewVeneurDigestFromProto creates a VeneurDigest from a DigestData proto
   *  The DigestData proto must have veneur_digest set correctly
   */
  fun newVeneurDigestFromProto(digestData: DigestData): VeneurDigestStruct {
    return object : VeneurDigestStruct {

      var mergingDigestData: MergingDigestData = MergingDigestData.ADAPTER.decode(digestData.veneur_digest)

      override var mergingDigest = mergingDigestInstance.newMergingFromData(mergingDigestData)
      override var count = digestData.count
      override var sum = digestData.sum
    }
  }
}