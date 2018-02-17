package misk.web.mediatype

import misk.containsExactly
import okhttp3.MediaType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Collections.shuffle

internal class MediaRangeTest {
  @Test
  fun parseAllWildcards() {
    val mediaRange = MediaRange.parse("*/*")
    assertThat(mediaRange.type).isEqualTo(MediaRange.WILDCARD)
    assertThat(mediaRange.subtype).isEqualTo(MediaRange.WILDCARD)
    assertThat(mediaRange.charset).isNull()
    assertThat(mediaRange.extensions).isEmpty()
    assertThat(mediaRange.parameters).isEmpty()
    assertThat(mediaRange.qualityFactor).isCloseTo(1.0, Offset.offset(0.01))
  }

  @Test
  fun parseSubtypeWildcard() {
    val mediaRange = MediaRange.parse("text / *")
    assertThat(mediaRange.type).isEqualTo("text")
    assertThat(mediaRange.subtype).isEqualTo(MediaRange.WILDCARD)
    assertThat(mediaRange.charset).isNull()
    assertThat(mediaRange.extensions).isEmpty()
    assertThat(mediaRange.parameters).isEmpty()
    assertThat(mediaRange.qualityFactor).isCloseTo(1.0, Offset.offset(0.01))
  }

  @Test
  fun parseTypeSubtypeOnly() {
    val mediaRange = MediaRange.parse("application/json")
    assertThat(mediaRange.type).isEqualTo("application")
    assertThat(mediaRange.subtype).isEqualTo("json")
    assertThat(mediaRange.charset).isNull()
    assertThat(mediaRange.extensions).isEmpty()
    assertThat(mediaRange.parameters).isEmpty()
    assertThat(mediaRange.qualityFactor).isCloseTo(1.0, Offset.offset(0.01))
  }

  @Test
  fun parseWithParameters() {
    val mediaRange = MediaRange.parse("text/html;level = 1 ; strict = true")
    assertThat(mediaRange.type).isEqualTo("text")
    assertThat(mediaRange.subtype).isEqualTo("html")
    assertThat(mediaRange.charset).isNull()
    assertThat(mediaRange.extensions).isEmpty()
    assertThat(mediaRange.parameters).containsExactly("level" to "1", "strict" to "true")
    assertThat(mediaRange.qualityFactor).isCloseTo(1.0, Offset.offset(0.01))
  }

  @Test
  fun parseWithCharsetParameter() {
    val mediaRange = MediaRange.parse("text/html;level = 1 ; strict = true;charset=us-ascii")
    assertThat(mediaRange.type).isEqualTo("text")
    assertThat(mediaRange.subtype).isEqualTo("html")
    assertThat(mediaRange.charset).isEqualTo(Charsets.US_ASCII)
    assertThat(mediaRange.extensions).isEmpty()
    assertThat(mediaRange.parameters).containsExactly("level" to "1", "strict" to "true")
    assertThat(mediaRange.qualityFactor).isCloseTo(1.0, Offset.offset(0.01))
  }

  @Test
  fun parseWithQualityFactor() {
    val mediaRange = MediaRange.parse(
        "text/html;level = 1 ; strict = true;charset=us-ascii; q = 0.45"
    )
    assertThat(mediaRange.type).isEqualTo("text")
    assertThat(mediaRange.subtype).isEqualTo("html")
    assertThat(mediaRange.charset).isEqualTo(Charsets.US_ASCII)
    assertThat(mediaRange.extensions).isEmpty()
    assertThat(mediaRange.parameters).containsExactly("level" to "1", "strict" to "true")
    assertThat(mediaRange.qualityFactor).isCloseTo(0.45, Offset.offset(0.01))
  }

  @Test
  fun parseWithExtensions() {
    val mediaRange = MediaRange.parse(
        "text/html;level = 1 ; strict = true;charset=us-ascii; q = 0.45; ext1=79; ext2=blerp"
    )
    assertThat(mediaRange.type).isEqualTo("text")
    assertThat(mediaRange.subtype).isEqualTo("html")
    assertThat(mediaRange.charset).isEqualTo(Charsets.US_ASCII)
    assertThat(mediaRange.extensions).containsExactly("ext1" to "79", "ext2" to "blerp")
    assertThat(mediaRange.parameters).containsExactly("level" to "1", "strict" to "true")
    assertThat(mediaRange.qualityFactor).isCloseTo(0.45, Offset.offset(0.01))
  }

  @Test
  fun wildcardTypeWithSpecificSubType() {
    assertThrows(IllegalArgumentException::class.java) {
      MediaRange.parse("*/html")
    }
  }

  @Test
  fun blankType() {
    assertThrows(IllegalArgumentException::class.java) {
      MediaRange.parse("/html")
    }
  }

  @Test
  fun blankSubType() {
    assertThrows(IllegalArgumentException::class.java) {
      MediaRange.parse("text/")
    }
  }

  @Test
  fun noTypeSubType() {
    assertThrows(IllegalArgumentException::class.java) {
      MediaRange.parse("*")
    }
  }

  @Test
  fun blankNameToken() {
    assertThrows(IllegalArgumentException::class.java) {
      MediaRange.parse("text/html;=foo")
    }
  }

  @Test
  fun blankValueToken() {
    assertThrows(IllegalArgumentException::class.java) {
      MediaRange.parse("text/html;bar=")
    }
  }

  @Test
  fun multipleQualityFactors() {
    assertThrows(IllegalArgumentException::class.java) {
      MediaRange.parse("text/html;q=0.4;q=0.56")
    }
  }

  @Test
  fun charsetInExtensions() {
    assertThrows(IllegalArgumentException::class.java) {
      MediaRange.parse("text/html;q=0.4;charset=utf-8")
    }
  }

  @Test
  fun matchTypeWildcard() {
    val mediaRange = MediaRange.parse("*/*")
    assertThat(mediaRange.matcher(MediaType.parse("text/html")!!)).isNotNull()
    assertThat(mediaRange.matcher(MediaType.parse("text/plain")!!)).isNotNull()
    assertThat(mediaRange.matcher(MediaType.parse("application/json")!!)).isNotNull()
  }

  @Test
  fun matchSubtypeWildcard() {
    val mediaRange = MediaRange.parse("text/*")
    assertThat(mediaRange.matcher(MediaType.parse("text/html")!!)).isNotNull()
    assertThat(mediaRange.matcher(MediaType.parse("text/plain")!!)).isNotNull()
    assertThat(mediaRange.matcher(MediaType.parse("application/json")!!)).isNull()
  }

  @Test
  fun matchExactTypeSubtype() {
    val mediaRange = MediaRange.parse("text/html")
    assertThat(mediaRange.matcher(MediaType.parse("text/html")!!)).isNotNull()
    assertThat(mediaRange.matcher(MediaType.parse("text/plain")!!)).isNull()
    assertThat(mediaRange.matcher(MediaType.parse("application/json")!!)).isNull()
  }

  @Test
  fun matchNoCharsetInMediaType() {
    val mediaRange = MediaRange.parse("text/html;charset=utf-8")
    val mediaType = MediaType.parse("text/html")!!
    val matcher = mediaRange.matcher(mediaType)

    assertThat(matcher).isNotNull()
    assertThat(matcher!!.matchesCharset).isFalse()
  }

  @Test
  fun matchNoCharsetInMediaRange() {
    val mediaRange = MediaRange.parse("text/html")
    val mediaType = MediaType.parse("text/html;charset=utf-8")!!
    val matcher = mediaRange.matcher(mediaType)

    assertThat(matcher).isNotNull()
    assertThat(matcher!!.matchesCharset).isFalse()
  }

  @Test
  fun charsetsDoNotMatch() {
    val mediaRange = MediaRange.parse("text/html;charset=us-ascii")
    val mediaType = MediaType.parse("text/html;charset=utf-8")!!
    val matcher = mediaRange.matcher(mediaType)

    assertThat(matcher).isNull()
  }

  @Test
  fun charsetsMatch() {
    val mediaRange = MediaRange.parse("text/html;charset=us-ascii")
    val mediaType = MediaType.parse("text/html;charset=us-ascii")!!
    val matcher = mediaRange.matcher(mediaType)

    assertThat(matcher).isNotNull()
    assertThat(matcher!!.matchesCharset).isTrue()
  }

  @Test
  fun compareTo() {
    val r1 = MediaRange.parse("*/*")
    val r2 = MediaRange.parse("text/*")
    val r3 = MediaRange.parse("text/html")
    val r4 = MediaRange.parse("text/html;charset=utf-8;level=1")
    val r5 = MediaRange.parse("text/html;charset=utf-8;level=1;q=0.5;ext1=one")
    val r6 = MediaRange.parse("text/html;charset=utf-8;level=1;f=zed")

    val unsorted = mutableListOf(r1, r2, r3, r4, r5, r6)
    shuffle(unsorted)

    val sorted = unsorted.sorted()
    assertThat(sorted).containsExactly(r6, r5, r4, r3, r2, r1)
  }

  // From https://developer.mozilla.org/en-US/docs/Web/HTTP/Content_negotiation/List_of_default_Accept_values
  @Test
  fun parsesDefaultBrowserValues() {
    MediaRange.parseRanges("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

    MediaRange.parseRanges(
        "application/xml,application/xhtml+xml,text/html;q=0.9, text/plain;q=0.8,image/png,*/*;q=0.5"
    )
    MediaRange.parseRanges("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    MediaRange.parseRanges(
        "image/jpeg, application/x-ms-application, image/gif, application/xaml+xml, image/pjpeg, application/x-ms-xbap, application/x-shockwave-flash, application/msword, */*"
    )
    MediaRange.parseRanges("text/html, application/xhtml+xml, image/jxr, */*")
    MediaRange.parseRanges(
        "text/html, application/xml;q=0.9, application/xhtml+xml, image/png, image/webp, image/jpeg, image/gif, image/x-xbitmap, */*;q=0.1"
    )
  }
}
