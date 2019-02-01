package misk.moshi

import com.squareup.moshi.JsonAdapter
import misk.inject.KAbstractModule
import misk.mockito.Mockito.mock
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
internal class MoshiAdapterModuleTest {
  val mock1 = mock<Any>()
  val mock2 = mock<JsonAdapter.Factory>()

  @MiskTestModule
  val module = TestingModule(mock1, mock2)

  @MoshiJsonAdapter @Inject lateinit var jsonAdapters: List<Any>

  class TestingModule(val mock1: Any,val  mock2: JsonAdapter.Factory) : KAbstractModule() {
    override fun configure() {
      install(MoshiAdapterModule(mock1))
      install(MoshiAdapterModule(mock2))
    }
  }

  @Test
  fun `test the adapters were injected on the right order`() {
    assertThat(jsonAdapters).containsExactly(mock1, mock2)
  }
}
