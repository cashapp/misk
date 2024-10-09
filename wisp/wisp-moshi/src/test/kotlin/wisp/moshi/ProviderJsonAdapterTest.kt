package wisp.moshi

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import com.google.inject.Provider as GuiceProvider
import javax.inject.Provider as JavaxProvider
import jakarta.inject.Provider as JakartaProvider

class ProviderJsonAdapterTest {

  @Test
  fun `happy path`() {
    val adapter = buildMoshi(listOf(ProviderJsonAdapterFactory())).adapter<Alpha>()

    val fromModel = Alpha(
      guice = { "bingo" },
      javax = { "charlie" },
      jakarta = { "delta" },
    )
    val toJson = """{"guice":"bingo","javax":"charlie","jakarta":"delta"}"""
    val actualJson = adapter.toJson(fromModel)
    assertEquals(toJson, actualJson)

    val jsonModel = adapter.fromJson(actualJson)
    assertEquals(fromModel.guice.get(), jsonModel?.guice?.get())
    assertEquals(fromModel.javax.get(), jsonModel?.javax?.get())
    assertEquals(fromModel.jakarta.get(), jsonModel?.jakarta?.get())
  }

  data class Alpha(
    val guice: GuiceProvider<String>,
    val javax: JavaxProvider<String>,
    val jakarta: JakartaProvider<String>,
  )

}
