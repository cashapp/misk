package misk.tracing.interceptors

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TextMultimapExtractAdapterTest {

  @Test
  fun flattensMultimap() {
    val multimap = mapOf(Pair("a", listOf("1", "2")), Pair("b", listOf("3")))
    val adapter = TextMultimapExtractAdapter(multimap)

    val pairs: MutableList<Pair<String, String>> = mutableListOf()
    adapter.iterator().forEach { pairs.add(Pair(it.key, it.value)) }

    assertThat(pairs).isEqualTo(
      listOf(
        Pair("a", "1"),
        Pair("a", "2"),
        Pair("b", "3")
      )
    )
  }

  @Test
  fun emptyMultimap() {
    val multimap = mapOf<String, List<String>>()
    val adapter = TextMultimapExtractAdapter(multimap)

    assertThat(adapter.iterator().hasNext()).isFalse()
  }
}