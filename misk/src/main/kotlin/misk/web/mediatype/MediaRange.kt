package misk.web.mediatype

import okhttp3.MediaType
import java.nio.charset.Charset

/** An RFC-2616 media range */
data class MediaRange(
    val type: String,
    val subtype: String,
    val charset: Charset? = null,
    val qualityFactor: Double = 1.0,
    val parameters: Map<String, String> = mapOf(),
    val extensions: Map<String, String> = mapOf()
) {
  val wildcardCount: Int = computeWildcardCount(type, subtype)

  fun matches(mediaType: MediaType) = matcher(mediaType) != null

  fun matcher(mediaType: MediaType): Matcher? {
    val typeMatches = type == mediaType.type() || type == WILDCARD
    val subtypeMatches = subtype == mediaType.subtype() || subtype == WILDCARD
    if (!typeMatches || !subtypeMatches) {
      return null
    }

    if (charset == null || mediaType.charset() == null) {
      // The media type matches, but we can't compare charsets because either the range
      // or the media type lacks a specified charset
      return Matcher(this)
    }

    if (charset != mediaType.charset()) {
      // Both specify a charset but they don't match, so we don't match
      return null
    }

    return Matcher(this, true)
  }

  data class Matcher(val mediaRange: MediaRange, val matchesCharset: Boolean = false)

  companion object {
    const val WILDCARD = "*"
    val ALL_MEDIA = MediaRange(WILDCARD, WILDCARD)

    fun parse(s: String): MediaRange {
      val typeParametersAndExtensions = s.split(';')
      val typeParts = typeParametersAndExtensions[0].split('/')
      require(typeParts.size == 2) { "$s is not a valid media range" }

      val type = typeParts[0].trim()
      val subtype = typeParts[1].trim()
      require(!type.isEmpty()) { "$s is not a valid media range" }
      require(!subtype.isEmpty()) { "$s is not a valid media range" }
      require(type != WILDCARD || subtype == WILDCARD) { "$s is not a valid media range" }

      if (typeParametersAndExtensions.size == 1) {
        return MediaRange(type, subtype)
      }

      val parametersAndExtensions = typeParametersAndExtensions.drop(1).map {
        parseNameValue(it)
      }
      val parameters = LinkedHashMap<String, String>()
      val extensions = LinkedHashMap<String, String>()
      var charset: Charset? = null
      var qualityFactor = 1.0
      var inParameters = true

      for (p in parametersAndExtensions) {
        when {
          p.first == "q" -> {
            require(inParameters) {
              "$s is not a valid media range; quality factor specified multiple times"
            }

            qualityFactor = p.second.toDouble()
            inParameters = false
          }
          p.first == "charset" -> {
            require(inParameters) {
              "$s is not a valid media range; encountered charset parameter in extensions"
            }

            charset = Charset.forName(p.second.toUpperCase())
          }
          inParameters -> parameters.put(p.first, p.second)
          else -> extensions.put(p.first, p.second)
        }
      }

      return MediaRange(
          type,
          subtype,
          charset,
          qualityFactor,
          parameters.toMap(),
          extensions.toMap()
      )
    }

    private fun parseNameValue(s: String): Pair<String, String> {
      val parts = s.split('=')
      require(parts.size == 2) { "$s is not a valid name/value pair" }
      val name = parts[0].trim()
      val value = parts[1].trim()
      require(!name.isEmpty()) { "$s is not a valid name/value pair" }
      require(!value.isEmpty()) { "$s is not a valid name/value pair" }
      return name to value
    }

    private fun computeWildcardCount(type: String, subtype: String): Int {
      var count = 0
      if (type == Companion.WILDCARD) count++
      if (subtype == Companion.WILDCARD) count++
      return count
    }
  }
}

