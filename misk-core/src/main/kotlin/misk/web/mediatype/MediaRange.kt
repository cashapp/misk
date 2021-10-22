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
  val extensions: Map<String, String> = mapOf(),
  private val rawText: String
) : Comparable<MediaRange> {
  override fun compareTo(other: MediaRange): Int {
    val wildcardDiff = wildcardCount - other.wildcardCount
    if (wildcardDiff != 0) return wildcardDiff

    val parameterDiff = other.parameters.size - parameters.size
    if (parameterDiff != 0) return parameterDiff

    val extensionDiff = other.extensions.size - extensions.size
    if (extensionDiff != 0) return extensionDiff

    return 0
  }

  override fun toString() = rawText

  private val wildcardCount = when {
    type == WILDCARD -> 2
    subtype == WILDCARD -> 1
    else -> 0
  }

  fun matcher(mediaType: MediaType): Matcher? {
    val typeMatches = type == mediaType.type || type == WILDCARD || mediaType.type == WILDCARD
    val subtypeMatches =
      subtype == mediaType.subtype || subtype == WILDCARD || mediaType.subtype == WILDCARD
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

  class Matcher(
    val mediaRange: MediaRange,
    val matchesCharset: Boolean = false
  ) : Comparable<Matcher> {
    override fun compareTo(other: Matcher): Int {
      val mediaRangeComparison = mediaRange.compareTo(other.mediaRange)
      if (mediaRangeComparison != 0) return mediaRangeComparison

      if (matchesCharset && !other.matchesCharset) return -1
      if (!matchesCharset && other.matchesCharset) return 1

      return 0
    }

    override fun toString() = "$mediaRange; charset-match $matchesCharset"
  }

  companion object {
    const val WILDCARD = "*"
    val ALL_MEDIA = MediaRange(WILDCARD, WILDCARD, rawText = "*/*")

    fun parseRanges(s: String, swallowExceptions: Boolean = false): List<MediaRange> {
      return s.split(',').mapNotNull {
        try {
          parse(it)
        } catch (th: Throwable) {
          if (!swallowExceptions) {
            throw th
          }
          null
        }
      }
    }

    fun parse(s: String): MediaRange {
      val typeParametersAndExtensions = s.split(';')
      val typeParts = typeParametersAndExtensions[0].split('/')
      require(typeParts.size == 2) { "$s is not a valid media range" }

      val type = typeParts[0].trim()
      val subtype = typeParts[1].trim()
      require(type.isNotEmpty()) { "$s is not a valid media range" }
      require(subtype.isNotEmpty()) { "$s is not a valid media range" }
      require(type != WILDCARD || subtype == WILDCARD) { "$s is not a valid media range" }

      if (typeParametersAndExtensions.size == 1) {
        return MediaRange(type, subtype, rawText = s)
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
          inParameters -> parameters[p.first] = p.second
          else -> extensions[p.first] = p.second
        }
      }

      return MediaRange(
        type,
        subtype,
        charset,
        qualityFactor,
        parameters.toMap(),
        extensions.toMap(),
        s
      )
    }

    private fun parseNameValue(s: String): Pair<String, String> {
      val parts = s.split('=')
      require(parts.size == 2) { "$s is not a valid name/value pair" }
      val name = parts[0].trim()
      val value = parts[1].trim()
      require(name.isNotEmpty()) { "$s is not a valid name/value pair" }
      require(value.isNotEmpty()) { "$s is not a valid name/value pair" }
      return name to value
    }
  }
}
