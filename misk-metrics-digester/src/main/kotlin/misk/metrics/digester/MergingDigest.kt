package misk.metrics.digester

import com.squareup.digester.protos.tdigest.Centroid
import com.squareup.digester.protos.tdigest.MergingDigestData
import kotlin.math.PI
import kotlin.math.asin

/**
 * Provides an implementation of t-digest to be used to send histogram data
 *
 * Direct port of Veneur digest created by Stripe
 * https://github.com/stripe/veneur/blob/master/tdigest/merging_digest.go
 */
class MergingDigest(private val compression: Double) {

  // main list of centroids
  private val mainCentroids: MutableList<Centroid> = mutableListOf()
  // total weight of unmerged main centroids
  private var mainWeight: Double = 0.0

  // centroids that have been added but not yet merged into main list
  private val tempCentroids: MutableList<Centroid> = mutableListOf()
  // total weight of unmerged temp centroids
  private var tempWeight: Double = 0.0

  private var min: Double = Double.POSITIVE_INFINITY
  private var max: Double = Double.NEGATIVE_INFINITY

  /**
   * Constructs a MergingDigest with values initialized from MergingDigestData.
   * This should be the way to generate a MergingDigest from a serialized protobuf.
   */
  constructor(mergingDigestData: MergingDigestData) : this(mergingDigestData.compression) {
    mainCentroids.addAll(mergingDigestData.main_centroids)
    min = mergingDigestData.min
    max = mergingDigestData.max

    mainWeight = 0.0
    for (c in mainCentroids) {
      mainWeight += c.weight
    }
  }

  /**
   * Returns the estimated size of the temporary buffer based on compression value
   *
   * Function taken from stripes implementation of merging_digest
   * https://github.com/stripe/veneur/blob/master/tdigest/merging_digest.go
   */
  private fun estimateTempBuffer(compressionValue: Double): Int {
    // this heuristic comes from Dunning's paper
    // 925 is the maximum point of this quadratic equation
    val tempCompression = minOf(925.0, maxOf(20.0, compressionValue))
    return (7.5 + 0.37 * tempCompression - 2e-4 * tempCompression * tempCompression).toInt()
  }

  /**
   * Adds a new value to the t-digest, with a given weight that must be positive.
   * Infinities and NaN cannot be added.
   */
  fun add(value: Double, weight: Double) {
    require(value != Double.NaN && value != Double.POSITIVE_INFINITY && weight > 0) {
      "invalid value added"
    }

    if (tempCentroids.size == estimateTempBuffer(compression)) {
      mergeAllTemps()
    }

    min = minOf(min, value)
    max = maxOf(max, value)

    var next = Centroid(
        value,
        weight,
        mutableListOf<Double>()
    )

    tempCentroids.add(next)
    tempWeight += weight
  }

  /**
   * Combine the mainCentroids and tempCentroids in-place into mainCentroids
   */
  private fun mergeAllTemps() {
    // this optimization is really important! if you remove it, the main list
    // will get merged into itself every time this is called
    if (tempCentroids.size == 0) {
      return
    }

    // we iterate over both centroid lists from least to greatest mean, so first
    // we have to sort this one
    tempCentroids.sortBy { it.mean }
    var tempIndex = 0

    // total weight that the final t-digest will have, after everything is merged
    var totalWeight = mainWeight + tempWeight
    // how much weight has been merged so far
    var mergedWeight = 0.0
    // the index of the last quantile to be merged into the previous centroid
    // this value gets updated each time we split a new centroid out instead of
    // merging into the current one
    var lastMergedIndex = 0.0
    // since we will be merging in-place into td.mainCentroids, we need to keep
    // track of the indices of the remaining elements
    var actualMainCentroids = mainCentroids.toMutableList()
    mainCentroids.clear()
    // to facilitate the in-place merge, we will need a place to store the main
    // centroids that would be overwritten - we will use space from the start
    // of tempCentroids for this
    var swappedCentroids = mutableListOf<Centroid>()

    while ((actualMainCentroids.size + swappedCentroids.size != 0) || tempIndex < tempCentroids.size) {
      var nextTemp: Centroid = Centroid(
          Double.POSITIVE_INFINITY,
          0.0,
          mutableListOf<Double>()
      )
      if (tempIndex < tempCentroids.size) {
        nextTemp = tempCentroids[tempIndex]
      }

      var nextMain: Centroid = Centroid(
          Double.POSITIVE_INFINITY,
          Double.NEGATIVE_INFINITY,
          mutableListOf<Double>()
      )
      if (swappedCentroids.size != 0) {
        nextMain = swappedCentroids[0]
      } else if (actualMainCentroids.size != 0) {
        nextMain = actualMainCentroids[0]
      }

      if (nextMain.mean < nextTemp.mean) {
        if (actualMainCentroids.size != 0) {
          if (swappedCentroids.size != 0) {
            // if this came from swap, before merging, we have to save
            // the next main centroid at the end
            // this copy is probably the most expensive part of the
            // in-place merge, compared to merging into a separate buffer
            swappedCentroids.removeAt(0)
            swappedCentroids[(swappedCentroids.size) - 1] = actualMainCentroids[0]
          }
          actualMainCentroids.removeAt(0)
        } else {
          // the real main has been completely exhausted, so we're just
          // cleaning out swapped mains now
          swappedCentroids.removeAt(0)
        }

        lastMergedIndex = mergeOne(mergedWeight, totalWeight, lastMergedIndex, nextMain)
        mergedWeight += nextMain.weight
      } else {
        // before merging, we have to save the next main centroid somewhere
        // else, so that we don't overwrite it
        if (actualMainCentroids.size != 0) {
          swappedCentroids.add(actualMainCentroids[0])
          actualMainCentroids.removeAt(0)
        }
        tempIndex++

        lastMergedIndex = mergeOne(mergedWeight, totalWeight, lastMergedIndex, nextTemp)
        mergedWeight += nextTemp.weight
      }
    }

    tempCentroids.clear()
    tempWeight = 0.0
    mainWeight = totalWeight
  }

  /**
   * Merges a single centroid into the mergedCentroids list.
   * Note that "merging" sometimes creates a new centroid in the list, however
   * the length of the list has a strict upper bound (see constructor)
   */
  private fun mergeOne(
    beforeWeight: Double,
    totalWeight: Double,
    beforeIndex: Double,
    next: Centroid
  ): Double {

    // compute the quantile index of the element we're about to merge
    var nextIndex = indexEstimate((beforeWeight + next.weight) / totalWeight)

    if (nextIndex - beforeIndex > 1 || mainCentroids.size == 0) {
      // the new index is far away from the last index of the current centroid
      // therefore we cannot merge into the current centroid or it would
      // become too wide, so we will append a new centroid
      mainCentroids.add(next)
      // return the last index that was merged into the previous centroid
      return indexEstimate(beforeWeight / totalWeight)
    } else {
      // the new index fits into the range of the current centroid, so we
      // combine it into the current centroid's values
      // this computation is known as welford's method, the order matters
      // weight must be updated before mean

      var mainCentroidsTemp = mainCentroids[mainCentroids.size - 1]
      var builder = mainCentroidsTemp.newBuilder()
      builder.weight += next.weight
      builder.mean +=
          (next.mean - mainCentroids[mainCentroids.size - 1].mean) * next.weight / mainCentroids[mainCentroids.size - 1].weight

      // we did not create a new centroid, so the trailing index of the previous
      // centroid remains
      return beforeIndex
    }
  }

  /**
   * Given a quantile, estimate the index of the centroid that contains it using
   * the given compression
   */
  private fun indexEstimate(quantile: Double): Double {
    return compression * ((asin(2 * quantile - 1) / PI) + 0.5)
  }

  /**
   * Returns a value such that the fraction of values in td below that value is
   * approximately equal to quantile. Returns NaN if the digest is empty.
   */
  fun quantile(quantile: Double): Double {
    require(quantile in 0.toDouble().rangeTo(1.toDouble())) {
      "quantile out of bounds"
    }
    mergeAllTemps()

    // add up the weights of centroids in ascending order until we reach a
    // centroid that pushes us over the quantile
    var q = quantile * mainWeight
    var weightSoFar = 0.0
    var lowerBound = min

    val iterator = mainCentroids.iterator()

    for ((i, c) in iterator.withIndex()) {
      var upperBound = centroidUpperBound(i)
      if (q <= weightSoFar + c.weight) {
        // the target quantile is somewhere inside this centroid
        // we compute how much of this centroid's weight falls into the quantile
        var proportion = (q - weightSoFar) / c.weight
        // and interpolate what value that corresponds to inside a uniform
        // distribution
        return lowerBound + (proportion * (upperBound - lowerBound))
      }

      // the quantile is above this centroid, so sum the weight and carry on
      weightSoFar += c.weight
      lowerBound = upperBound
    }

    // should never be reached unless empty, since the final comparison is
    // q <= td.mainWeight
    return Double.NaN
  }

  /**
   * We assume each centroid contains a uniform distribution of values
   * the lower bound of the distribution is the midpoint between this centroid and
   * the previous one (or the minimum, if this is the lowest centroid)
   * similarly, the upper bound is the midpoint between this centroid and the
   * next one (or the maximum, if this is the greatest centroid)
   * this function returns the position of the upper bound (the lower bound is
   * equal to the upper bound of the previous centroid)
   * this assumption is justified empirically in dunning's paper
   * TODO: does this assumption actually apply to our implementation?
   */
  private fun centroidUpperBound(i: Int): Double {
    if (i != mainCentroids.size - 1) {
      return (mainCentroids[i + 1].mean + mainCentroids[i].mean) / 2
    } else {
      return max
    }
  }

  /**
   * Merge another digest into this one. Neither td nor other can be shared
   * concurrently during the execution of this method.
   */
  fun mergeFrom(other: MergingDigest) {

    // end inclusive
    var shuffledIndices = IntArray(other.mainCentroids.size) { it }.toMutableList()
    shuffledIndices.shuffle()

    for (i in shuffledIndices) {
      add(other.mainCentroids[i].mean, other.mainCentroids[i].weight)
    }

    // we did not merge other's temps, so we need to add those too
    // they're unsorted so there's no need to shuffle them
    for (centroid in other.tempCentroids) {
      add(centroid.mean, centroid.weight)
    }
  }

  /**
   * MergingDigestData contains all fields necessary to generate a MergingDigest.
   * This type should generally just be used when serializing MergingDigest's,
   * and doesn't have much of a purpose on its own.
   */
  fun data(): MergingDigestData {
    mergeAllTemps()
    return MergingDigestData(
        mainCentroids,
        compression,
        min,
        max
    )
  }
}
