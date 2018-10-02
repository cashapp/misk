package misk.digester

import com.google.protobuf.Int64Value
import misk.metrics.Histogram

class VeneurDigest
  constructor (
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: DoubleArray?
  ): Histogram {

  // VeneurDigest wraps an adapted t-digest implementation from Stripe's Veneur project.
  interface VeneurDigestStruct {
    var mergingDigest: MergingDigest.MergingDigestStruct
    var count: Long
    var sum: Double
  }

  var mergingDigestInstance = MergingDigest()

  //add adds a new observation to the t-digest
  fun Add(v: VeneurDigestStruct, value: Double) {
    mergingDigestInstance.Add(v.mergingDigest, value, 1.0)
    v.count += 1
    v.sum += value
  }

  //count returns the count of the number of observations recorded within the t-digest
  override fun Count(v: VeneurDigestStruct): Long {
    ///return buckets.max()?.toInt() ?: 0
    return v.count
  }

  //sum returns the sum of all values added into the digest, or NaN if no values have been added
  fun sum(v: VeneurDigestStruct): Double {
    if (v.count > 0) {
      return v.sum
    }
    return Double.NaN
  }

  //merges this t-digest into another t-digest
  fun mergeInto(v: VeneurDigestStruct, other: VeneurDigestStruct) {
    mergingDigestInstance.Merge(other.mergingDigest, v.mergingDigest)
    other.count += v.count
    other.sum += v.sum
  }

  //proto returns a representation fo the t-digest that can be later be reconstituted into an instance of the same type
  fun proto(v: VeneurDigestStruct) {
    //implementation for proto
    mergingDigestInstance.Data(v.mergingDigest)
  }

  fun NewVeneurDigest() {
    return
  }





  override fun record(duration: Double, vararg labelValues: String) {
    sum += duration
    TODO(
        "not implemented") //To change body of created functions use File | Settings | File Templates.
  }

}