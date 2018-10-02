package misk.digester

import kotlin.math.PI
import kotlin.math.asin

class MergingDigest() {

  interface Centroid { //TODO: need to set this up for proto
    var Mean:    Double   //`protobuf:"fixed64,1,opt,name=mean,proto3" json:"mean,omitempty"`
    var Weight:  Double   //`protobuf:"fixed64,2,opt,name=weight,proto3" json:"weight,omitempty"`
    var Samples: MutableList<Float> //`protobuf:"fixed64,3,rep,packed,name=samples" json:"samples,omitempty"`
  }

  interface MergingDigestData { //TODO: need to set this up for proto
    var MainCentroids: MutableList<Centroid> //`protobuf:"bytes,1,rep,name=main_centroids,json=mainCentroids" json:"main_centroids"`
    var Compression: Double //`protobuf:"fixed64,2,opt,name=compression,proto3" json:"compression,omitempty"`
    var Min: Double    //`protobuf:"fixed64,3,opt,name=min,proto3" json:"min,omitempty"`
    var Max: Double    //`protobuf:"fixed64,4,opt,name=max,proto3" json:"max,omitempty"`
  }

  interface MergingDigestObject {
    var compression: Double

    //main list of centroids
    var mainCentroids: MutableList<Centroid> //this list can be a mutableList instead?
    //total weight of unmerged main centroids
    var mainWeight: Double

    //centroids that have been added but not yet merged into main list
    var tempCentroids: MutableList<Centroid>
    //total weight of unmerged temp centroids
    var tempWeight: Double

    var min: Double
    var max: Double
  }

  // Initializes a new merging t-digest using the given compression parameter.
  // Lower compression values result in reduced memory consumption and less
  // precision, especially at the median. Values from 20 to 1000 are recommended
  // in Dunning's paper.
  fun NewMerging(compressionValue: Double): MergingDigestObject {
    // this is a provable upper bound on the size of the centroid list
    var sizeBound = ((PI * compressionValue / 2) + 0.5).toInt()

    return object : MergingDigestObject {
      override var compression = compressionValue
      override var mainCentroids =  mutableListOf<Centroid>()
      override var tempCentroids = mutableListOf<Centroid>()
      override var min = Double.NEGATIVE_INFINITY
      override var max = Double.POSITIVE_INFINITY

      //values are not declared in stripe's implementation
      override var mainWeight = 0.0
      override var tempWeight = 0.0
    }
  }

  //function taken from stripes implementation of merging_digest (https://github.com/stripe/veneur/blob/master/tdigest/merging_digest.go)
  fun estimateTempBuffer(compressionValue: Double): Int {
    // this heuristic comes from Dunning's paper
    // 925 is the maximum point of this quadratic equation
    var tempCompression = minOf(925.0, maxOf(20.0, compressionValue))
    return (7.5 + 0.37*tempCompression - 2e-4*tempCompression*tempCompression).toInt()
  }

  fun Add(mergingDigestObject: MergingDigestObject, value: Double, weight: Double) {
    if (Double.NaN == value || Double.POSITIVE_INFINITY == value || weight <= 0) {
      Exception("invalid value added")
    }

    if (mergingDigestObject.tempCentroids.size == estimateTempBuffer(mergingDigestObject.compression)) {
          //mergealltemps <- will we still need this?
    }

    mergingDigestObject.min = minOf(mergingDigestObject.min, value)
    mergingDigestObject.max = maxOf(mergingDigestObject.max, value)

    var next = object: Centroid {
      override var Mean = value
      override var Weight = weight
      override var Samples = mutableListOf<Float>()
    }

    mergingDigestObject.tempCentroids.add(next)
    mergingDigestObject.tempWeight += weight

  }

  fun mergeAllTemps(mergingDigestObject: MergingDigestObject) {
    // this optimization is really important! if you remove it, the main list
    // will get merged into itself every time this is called
    if (mergingDigestObject.tempCentroids.size == 0) {
      return
    }

    // we iterate over both centroid lists from least to greatest mean, so first
    // we have to sort this one
    mergingDigestObject.tempCentroids.sortBy { it.Mean  }
    var tempIndex = 0

    // total weight that the final t-digest will have, after everything is merged
    var totalWeight = mergingDigestObject.mainWeight + mergingDigestObject.tempWeight
    // how much weight has been merged so far
    var mergedWeight = 0.0
    // the index of the last quantile to be merged into the previous centroid
    // this value gets updated each time we split a new centroid out instead of
    // merging into the current one
    var lastMergedIndex = 0.0
    // since we will be merging in-place into td.mainCentroids, we need to keep
    // track of the indices of the remaining elements
    var actualMainCentroids = mergingDigestObject.mainCentroids
    mergingDigestObject.mainCentroids = mergingDigestObject.mainCentroids.subList(0, 0)
    // to facilitate the in-place merge, we will need a place to store the main
    // centroids that would be overwritten - we will use space from the start
    // of tempCentroids for this
    var swappedCentroids = mergingDigestObject.tempCentroids.subList(0, 0)

    while ((actualMainCentroids.size + swappedCentroids.size != 0) || tempIndex < mergingDigestObject.tempCentroids.size) {
      var nextTemp: Centroid = object: Centroid {
        override var Mean = Double.POSITIVE_INFINITY
        override var Weight = 0.0
        override var Samples = mutableListOf<Float>()
      }
      if (tempIndex < mergingDigestObject.tempCentroids.size) {
        nextTemp = mergingDigestObject.tempCentroids[tempIndex]
      }

      var nextMain: Centroid = object: Centroid{
        override var Mean = Double.POSITIVE_INFINITY
        override var Weight = Double.NEGATIVE_INFINITY
        override var Samples = mutableListOf<Float>()
    }
      if (swappedCentroids.size != 0) {
        nextMain = swappedCentroids[0]
      } else if (actualMainCentroids.size != 0) {
        nextMain = actualMainCentroids[0]
      }

      if (nextMain.Mean < nextTemp.Mean) {
        if (actualMainCentroids.size != 0) {
          if (swappedCentroids.size != 0) {
            // if this came from swap, before merging, we have to save
            // the next main centroid at the end
            // this copy is probably the most expensive part of the
            // in-place merge, compared to merging into a separate buffer
            swappedCentroids.removeAt(0)
            swappedCentroids[(swappedCentroids.size)-1] = actualMainCentroids[0]
          }
          actualMainCentroids.removeAt(0)
        } else {
          // the real main has been completely exhausted, so we're just
          // cleaning out swapped mains now
          swappedCentroids.removeAt(0)
        }

        lastMergedIndex = mergeOne(mergingDigestObject, mergedWeight, totalWeight, lastMergedIndex, nextMain)
        mergedWeight += nextMain.Weight
      } else {
        // before merging, we have to save the next main centroid somewhere
        // else, so that we don't overwrite it
        if (actualMainCentroids.size != 0) {
          swappedCentroids.add(actualMainCentroids[0])
          actualMainCentroids.removeAt(0)
        }
        tempIndex++

        lastMergedIndex = mergeOne(mergingDigestObject, mergedWeight, totalWeight, lastMergedIndex, nextTemp)
        mergedWeight += nextTemp.Weight
      }
    }

    mergingDigestObject.tempCentroids.clear()
    mergingDigestObject.tempWeight = 0.0
    mergingDigestObject.mainWeight = totalWeight
  }


  // merges a single centroid into the mergedCentroids list
  // note that "merging" sometimes creates a new centroid in the list, however
  // the length of the list has a strict upper bound (see constructor)
  fun mergeOne(
    mergingDigestObject: MergingDigestObject,
      beforeWeight: Double,
      totalWeight: Double,
      beforeIndex: Double,
      next: Centroid
  ): Double {

    // compute the quantile index of the element we're about to merge
    var nextIndex = indexEstimate(mergingDigestObject, (beforeWeight + next.Weight) / totalWeight)

    if (nextIndex-beforeIndex > 1 || mergingDigestObject.mainCentroids.size == 0) {
      // the new index is far away from the last index of the current centroid
      // therefore we cannot merge into the current centroid or it would
      // become too wide, so we will append a new centroid
      mergingDigestObject.mainCentroids.add(next)
      // return the last index that was merged into the previous centroid
      return indexEstimate(mergingDigestObject,beforeWeight / totalWeight)
    } else {
      // the new index fits into the range of the current centroid, so we
      // combine it into the current centroid's values
      // this computation is known as welford's method, the order matters
      // weight must be updated before mean
      mergingDigestObject.mainCentroids[mergingDigestObject.mainCentroids.size-1].Weight += next.Weight
      mergingDigestObject.mainCentroids[mergingDigestObject.mainCentroids.size-1].Mean += (next.Mean - mergingDigestObject.mainCentroids[mergingDigestObject.mainCentroids.size-1].Mean) * next.Weight / mergingDigestObject.mainCentroids[mergingDigestObject.mainCentroids.size-1].Weight

      // we did not create a new centroid, so the trailing index of the previous
      // centroid remains
      return beforeIndex
    }
  }

  fun indexEstimate(mergingDigestObject: MergingDigestObject, quantile: Double) : Double {
    return mergingDigestObject.compression * ((asin(2*quantile-1) / PI) + 0.5).toFloat()
  }


  // Returns a value such that the fraction of values in td below that value is
  // approximately equal to quantile. Returns NaN if the digest is empty.
  fun Quantile(mergingDigestObject: MergingDigestObject, quantile: Double): Double {
    if (quantile < 0 || quantile > 1) {
      throw IndexOutOfBoundsException("quantile out of bounds")
    }
    mergeAllTemps(mergingDigestObject)

    // add up the weights of centroids in ascending order until we reach a
    // centroid that pushes us over the quantile
    var q = quantile * mergingDigestObject.mainWeight
    var weightSoFar = 0.0
    var lowerBound = mergingDigestObject.min

    val iterator = mergingDigestObject.mainCentroids.iterator()

    for((i, c) in iterator.withIndex()) {
      var upperBound = centroidUpperBound(mergingDigestObject, i)
      if (q <= weightSoFar+c.Weight) {
        // the target quantile is somewhere inside this centroid
        // we compute how much of this centroid's weight falls into the quantile
        var proportion = (q - weightSoFar) / c.Weight
        // and interpolate what value that corresponds to inside a uniform
        // distribution
        return lowerBound + (proportion * (upperBound - lowerBound))
      }

      // the quantile is above this centroid, so sum the weight and carry on
      weightSoFar += c.Weight
      lowerBound = upperBound
    }

    // should never be reached unless empty, since the final comparison is
    // q <= td.mainWeight
    return Double.NaN
  }

  // we assume each centroid contains a uniform distribution of values
  // the lower bound of the distribution is the midpoint between this centroid and
  // the previous one (or the minimum, if this is the lowest centroid)
  // similarly, the upper bound is the midpoint between this centroid and the
  // next one (or the maximum, if this is the greatest centroid)
  // this function returns the position of the upper bound (the lower bound is
  // equal to the upper bound of the previous centroid)
  // this assumption is justified empirically in dunning's paper
  // TODO: does this assumption actually apply to our implementation?
  fun centroidUpperBound(mergingDigestObject: MergingDigestObject, i: Int): Double {
    if (i != mergingDigestObject.mainCentroids.size-1) {
      return (mergingDigestObject.mainCentroids[i+1].Mean + mergingDigestObject.mainCentroids[i].Mean) / 2
    } else {
      return mergingDigestObject.max
    }
  }

  // Merge another digest into this one. Neither td nor other can be shared
  // concurrently during the execution of this method.
  fun Merge( mergingDigestObject: MergingDigestObject, other: MergingDigestObject) {

    //end inclusive
    var shuffledIndices = IntRange(0,other.mainCentroids.size - 1)
    shuffledIndices.shuffled()

    for (i in shuffledIndices) {
        Add(mergingDigestObject, other.mainCentroids[i].Mean, other.mainCentroids[i].Weight)
    }

    // we did not merge other's temps, so we need to add those too
    // they're unsorted so there's no need to shuffle them
    for (centroid in other.tempCentroids) {
      Add(mergingDigestObject, centroid.Mean, centroid.Weight)
    }
  }

  // MergingDigestData contains all fields necessary to generate a MergingDigest.
  // This type should generally just be used when serializing MergingDigest's,
  // and doesn't have much of a purpose on its own.
  fun Data(mergingDigestObject: MergingDigestObject): MergingDigestData {
    mergeAllTemps(mergingDigestObject)
    return object: MergingDigestData{
        override var MainCentroids =  mergingDigestObject.mainCentroids
        override var Compression = mergingDigestObject.compression
        override var Min = mergingDigestObject.min
        override var Max = mergingDigestObject.max
    }
  }

}