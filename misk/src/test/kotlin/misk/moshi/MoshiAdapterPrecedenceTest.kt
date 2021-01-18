package misk.moshi

import com.google.inject.Module
import com.google.inject.util.Modules
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import misk.ServiceManagerModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
internal class MoshiAdapterPrecedenceTest {
  private val log = mutableListOf<String>()

  private val factory1 = JsonAdapter.Factory { type, _, _ ->
    log += "factory1 create $type"
    null
  }

  @Suppress("unused", "UNUSED_PARAMETER")
  private val adapter2 = object {
    @ToJson fun pizzaToJson(pizza: Pizza): String = error("unused")
    @FromJson fun jsonToPizza(pizza: String): Pizza = error("unused")
  }

  private val factory3 = JsonAdapter.Factory { type, _, _ ->
    log += "factory3 create $type"
    null
  }

  @MiskTestModule
  val module: Module = Modules.combine(
    MoshiModule(),
    MoshiAdapterModule(factory1),
    MoshiAdapterModule(adapter2),
    MoshiAdapterModule(factory3),
    ServiceManagerModule()
  )

  @Inject lateinit var moshi: Moshi

  class Pizza(val size: String)

  @Test
  fun `test the adapters were injected on the right order`() {
    val adapter = moshi.adapter<Pizza>()
    println(adapter)

    assertThat(log).containsExactly(
      "factory1 create ${Pizza::class.java}",
      // factory3 not consulted because adapter2 matched.
      "factory1 create ${String::class.java}",
      "factory3 create ${String::class.java}"
    )
  }
}