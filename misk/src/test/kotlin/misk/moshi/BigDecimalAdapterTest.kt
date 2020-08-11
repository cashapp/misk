package misk.moshi

import com.squareup.moshi.Moshi
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.moshi.adapters.BigDecimalAdapter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import javax.inject.Inject

@MiskTest(startService = false)
internal class BigDecimalAdapterTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  lateinit var moshi: Moshi

  @Test
  fun `adapter converts from and to json`() {
    val json = "\"5.5\""
    val value = BigDecimal("5.5")
    val jsonAdapter = moshi.adapter<BigDecimal>()
    assertThat(jsonAdapter.toJson(value)).isEqualTo(json)
    assertThat(jsonAdapter.fromJson(json)).isEqualTo(value)
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(MoshiAdapterModule(BigDecimalAdapter))
    }
  }
}
