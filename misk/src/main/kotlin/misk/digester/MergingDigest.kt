package misk.digester

import kotlin.math.PI

class MergingDigest() {

  object Centroid { //need to set this up for proto
    var Mean:    Float   `protobuf:"fixed64,1,opt,name=mean,proto3" json:"mean,omitempty"`
    var Weight:  Float   `protobuf:"fixed64,2,opt,name=weight,proto3" json:"weight,omitempty"`
    var Samples: Float[] `protobuf:"fixed64,3,rep,packed,name=samples" json:"samples,omitempty"`
  }

  /*object MergingDigestObject { //should this be an interface
    var compression: Float = Float.NaN

    //main list of centroids
    var mainCentroids: MutableList<Centroid> = mutableListOf()
    //total weight of unmerged main centroids
    var mainWeight: Float = Float.NaN

    //centroids that have been added but not yet merged into main list
    var tempCentroids: MutableList<Centroid> = mutableListOf()
    //total weight of unmerged temp centroids
    var tempWeight: Float = Float.NaN

    var min: Float = Float.NaN
    var max: Float = Float.NaN
  }*/

  interface MergingDigestObject {
    var compression: Float

    //main list of centroids
    var mainCentroids: List<Centroid>
    //total weight of unmerged main centroids
    var mainWeight: Float

    //centroids that have been added but not yet merged into main list
    var tempCentroids: List<Centroid>
    //total weight of unmerged temp centroids
    var tempWeight: Float

    var min: Float
    var max: Float
  }



  // Initializes a new merging t-digest using the given compression parameter.
  // Lower compression values result in reduced memory consumption and less
  // precision, especially at the median. Values from 20 to 1000 are recommended
  // in Dunning's paper.
  fun NewMerging(compressionValue: Float): MergingDigestObject {
    // this is a provable upper bound on the size of the centroid list
    var sizeBound = ((PI * compressionValue / 2) + 0.5).toInt()


    return object : MergingDigestObject {
      override var compression = compressionValue
      override var mainCentroids = List(sizeBound) { Centroid }
      override var tempCentroids = List(estimateTempBuffer(compressionValue)) { Centroid }
      override var min = Float.MAX_VALUE * -1
      override var max = Float.MAX_VALUE

      //values are not declared in stripe's implementation
      override var mainWeight = 0f
      override var tempWeight = 0f
    }
  }

  //function taken from stripes implementation of merging_digest (https://github.com/stripe/veneur/blob/master/tdigest/merging_digest.go)
  fun estimateTempBuffer(compressionValue: Float): Int {
    // this heuristic comes from Dunning's paper
    // 925 is the maximum point of this quadratic equation
    var tempCompression = minOf(925f, maxOf(20f, compressionValue))
    return (7.5 + 0.37*tempCompression - 2e-4*tempCompression*tempCompression).toInt()
  }

  fun add(value: Float, weight: Float) {
    if (Float.NaN == value || Float.POSITIVE_INFINITY == value || weight <= 0) {
      Exception("invalid value added")
    }

    if

  fun merge(other: MergingDigest) {

  }


}