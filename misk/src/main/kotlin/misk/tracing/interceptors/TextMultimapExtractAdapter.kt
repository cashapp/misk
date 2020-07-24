package misk.tracing.interceptors

import io.opentracing.propagation.TextMap

/**
 * Provides an interface for Tracer.extract() to read from a multimap datasource
 * (i.e. HTTP headers provided via OkHttp)
 */
internal class TextMultimapExtractAdapter(
  private val multimap: Map<String, List<String>>
) : TextMap {
  override fun put(key: String, value: String) {
    throw UnsupportedOperationException("Cannot put into readonly data source")
  }

  override fun iterator(): MutableIterator<MutableMap.MutableEntry<String, String>> {
    return multimap.flatMap {
      it.value.map { mapValue -> ExtractEntry(it.key, mapValue) }
    }.toMutableList().iterator()
  }

  private class ExtractEntry(
    override val key: String,
    override var value: String
  ) : MutableMap.MutableEntry<String, String> {
    override fun setValue(newValue: String): String {
      value = newValue
      return value
    }
  }
}
