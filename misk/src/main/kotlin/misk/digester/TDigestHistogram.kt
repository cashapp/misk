package misk.digester

import misk.metrics.Histogram
import java.util.concurrent.atomic.DoubleAdder

class TDigestHistogram
  constructor (
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: DoubleArray?
  ): Histogram, VeneurDigest{ //this class is equivelent to veneur digest


  init {

  }

  var mergingDigest: MergingDigest = MergingDigest()

  override var sum: Double = 0.0 //does this require to be float?
  override var count: Int = 0

  //add adds a new observation to the t-digest
  fun add(value: Float) {
    mergingDigest.add(value, 1.0f)
    count += 1
    sum += value
  }

  //count returns the count of the number of observations recorded within the t-digest
  override fun count(): Int {
    ///return buckets.max()?.toInt() ?: 0
    return count
  }

  //sum returns the sum of all values added into the digest, or NaN if no values have been added
  fun sum(): Double {
    if (count > 0) {
      return sum
    }
    return Double.NaN
  }

  //merges this t-digest into another t-digest
  fun mergeInto(other: VeneurDigest) {
    other.mergingDigest.merge(mergingDigest)
    other.count += count
    other.sum += sum
  }

  //proto returns a representation fo the t-digest that can be later be reconstituted into an instance of the same type
  fun proto() {
    //implementation for proto
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