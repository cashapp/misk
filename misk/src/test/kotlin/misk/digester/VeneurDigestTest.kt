package misk.digester

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VeneurDigestTest {

  @BeforeEach
  fun sendRequests() {

  }

  var veneurDigest = VeneurDigest()
  var mergingDigest = MergingDigest()

  @Test
  fun testVeneurDigest() {

    var digest = veneurDigest.newDefaultVeneurDigest()
    Assertions.assertThat(mergingDigest.quantile(digest.mergingDigest, 0.5)).isEqualTo(Double.NaN)
    Assertions.assertThat(veneurDigest.sum(digest)).isEqualTo(Double.NaN)
    Assertions.assertThat(veneurDigest.count(digest)).isEqualTo(0)

    veneurDigest.add(digest, 10.0)
    veneurDigest.add(digest, 20.0)
    Assertions.assertThat(mergingDigest.quantile(digest.mergingDigest, 0.5)).isEqualTo(15.0)
    Assertions.assertThat(veneurDigest.count(digest)).isEqualTo(2)
    Assertions.assertThat(veneurDigest.sum(digest)).isEqualTo(30.0)

  }

  interface TestCasesStructure {
    var sourceVals: Array<Double>
    var destVals: Array<Double>
    var expectedMedian: Double
    var expectedSum: Double
  }

  @Test
  fun testVeneurDigest_MergeInto() {
    val testCases: Array<TestCasesStructure>
        = arrayOf(
          object: TestCasesStructure {
            override var sourceVals = emptyArray<Double>()
            override var destVals = emptyArray<Double>()
            override var expectedMedian = Double.NaN
            override var expectedSum = Double.NaN
          },
          object: TestCasesStructure {
            override var sourceVals = emptyArray<Double>()
            override var destVals = arrayOf(30.0, 40.0)
            override var expectedMedian = 35.0
            override var expectedSum = 70.0
          },
          object: TestCasesStructure {
            override var sourceVals = arrayOf(10.0, 20.0)
            override var destVals = emptyArray<Double>()
            override var expectedMedian = 15.0
            override var expectedSum = 30.0
          },
          object: TestCasesStructure {
            override var sourceVals = arrayOf(10.0, 20.0)
            override var destVals = arrayOf(30.0, 40.0)
            override var expectedMedian = 25.0
            override var expectedSum = 100.0
          }
        )

    for(tc in testCases) {
      var src = veneurDigest.newDefaultVeneurDigest()
      var dest = veneurDigest.newDefaultVeneurDigest()

      for (v in tc.sourceVals) {
        veneurDigest.add(src, v)
      }
      for (v in tc.destVals) {
        veneurDigest.add(dest, v)
      }

      val srcMedian = mergingDigest.quantile(src.mergingDigest, 0.5)
      val srcSum = veneurDigest.sum(src)
      val srcCount = veneurDigest.count(src)

      veneurDigest.mergeInto(src, dest)

      // Check that src is unchanged
      Assertions.assertThat(srcMedian).isEqualTo(mergingDigest.quantile(src.mergingDigest, 0.5))
      Assertions.assertThat(srcSum).isEqualTo(veneurDigest.sum(src))
      Assertions.assertThat(srcCount).isEqualTo(veneurDigest.count(src))

      //Check dest
      Assertions.assertThat(tc.expectedMedian).isEqualTo(mergingDigest.quantile(dest.mergingDigest, 0.5))
      Assertions.assertThat(tc.expectedSum).isEqualTo(veneurDigest.sum(dest))
      Assertions.assertThat((tc.sourceVals.size + tc.destVals.size).toLong()).isEqualTo(veneurDigest.count(dest))
    }
  }

  @Test
  fun testVeneurDigest_Proto() {
    var digest = veneurDigest.newDefaultVeneurDigest()
    veneurDigest.add(digest, 10.0)
    veneurDigest.add(digest, 20.0)
    veneurDigest.add(digest, 30.0)
    veneurDigest.add(digest, 40.0)

    val proto = veneurDigest.proto(digest)

    val deserialized = veneurDigest.newVeneurDigestFromProto(proto)

    Assertions.assertThat(mergingDigest.quantile(deserialized.mergingDigest, 0.1)).isEqualTo(mergingDigest.quantile(digest.mergingDigest, 0.1))
    Assertions.assertThat(mergingDigest.quantile(deserialized.mergingDigest, 0.99)).isEqualTo(mergingDigest.quantile(digest.mergingDigest, 0.99))
    Assertions.assertThat(veneurDigest.sum(deserialized)).isEqualTo(veneurDigest.sum(digest))
    Assertions.assertThat(veneurDigest.count(deserialized)).isEqualTo(veneurDigest.count(digest))
  }

}