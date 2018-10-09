package misk.digester

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VeneurDigestTest {

  @Test
  fun testVeneurDigest() {
    var digest = VeneurDigest()
    assertThat(digest.mergingDigest().quantile( 0.5)).isEqualTo(Double.NaN)
    assertThat(digest.sum()).isEqualTo(Double.NaN)
    assertThat(digest.count()).isEqualTo(0)

    digest.add(10.0)
    digest.add(20.0)
    assertThat(digest.mergingDigest().quantile(0.5)).isEqualTo(15.0)
    assertThat(digest.count()).isEqualTo(2)
    assertThat(digest.sum()).isEqualTo(30.0)

  }

  private data class VeneurDigestMergeTestClass (
    var sourceVals: Array<Double> = emptyArray(),
    var destVals: Array<Double> = emptyArray(),
    var expectedMedian: Double = Double.NaN,
    var expectedSum: Double = Double.NaN)

  @Test
  fun testVeneurDigest_MergeInto() {
    val testCases: Array<VeneurDigestMergeTestClass>
        = arrayOf(
          VeneurDigestMergeTestClass(),
          VeneurDigestMergeTestClass(
            emptyArray(),
            arrayOf(30.0, 40.0),
            35.0,
            70.0
          ),
          VeneurDigestMergeTestClass(
           arrayOf(10.0, 20.0),
            emptyArray(),
            15.0,
            30.0
          ),
          VeneurDigestMergeTestClass(
            arrayOf(10.0, 20.0),
            arrayOf(30.0, 40.0),
            25.0,
            100.0
          )
        )

    for(tc in testCases) {
      var src = VeneurDigest()
      var dest = VeneurDigest()

      for (v in tc.sourceVals) {
        src.add(v)
      }
      for (v in tc.destVals) {
        dest.add(v)
      }

      val srcMedian = src.mergingDigest().quantile(0.5)
      val srcSum = src.sum()
      val srcCount = src.count()

      src.mergeInto(dest)

      // Check that src is unchanged
      assertThat(srcMedian).isEqualTo(src.mergingDigest().quantile(0.5))
      assertThat(srcSum).isEqualTo(src.sum())
      assertThat(srcCount).isEqualTo(src.count())

      // Check dest
      assertThat(tc.expectedMedian).isEqualTo(dest.mergingDigest().quantile( 0.5))
      assertThat(tc.expectedSum).isEqualTo(dest.sum())
      assertThat((tc.sourceVals.size + tc.destVals.size).toLong()).isEqualTo(dest.count())
    }
  }

  @Test
  fun testVeneurDigest_Proto() {
    var digest = VeneurDigest()
    digest.add( 10.0)
    digest.add( 20.0)
    digest.add(30.0)
    digest.add( 40.0)

    val proto = digest.proto()

    val deserialized = VeneurDigest(proto)

    assertThat(deserialized.sum()).isEqualTo(digest.sum())
    assertThat(deserialized.count()).isEqualTo(digest.count())
    assertThat(deserialized.mergingDigest().quantile(0.1)).isEqualTo(digest.mergingDigest().quantile(0.1))
    assertThat(deserialized.mergingDigest().quantile(0.99)).isEqualTo(digest.mergingDigest().quantile(0.99))
  }

}