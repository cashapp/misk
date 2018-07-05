package com.squareup.urlshortener

import misk.exceptions.BadRequestException
import misk.exceptions.NotFoundException
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.ResponseBody
import okhttp3.HttpUrl
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class ShortenUrlWebActionTest {
  @MiskTestModule
  val module = UrlShortenerTestModule()

  @Inject lateinit var endpointConfig: EndpointConfig
  @Inject lateinit var createShortUrlWebAction: CreateShortUrlWebAction
  @Inject lateinit var shortUrlWebAction: ShortUrlWebAction

  @Test
  fun shorten() {
    val longUrl = "https://github.com/square/misk/pull/216"

    val createResponse = createShortUrlWebAction.createShortUrl(
        CreateShortUrlWebAction.Request(longUrl))
    val shortUrl = HttpUrl.parse(createResponse.short_url)!!
    assertThat(shortUrl.toString()).startsWith(endpointConfig.base_url)

    val redirectResponse = shortUrlWebAction.follow(shortUrl.pathSegments()[0])
    assertThat(redirectResponse.statusCode).isEqualTo(302)
    assertThat(redirectResponse.headers["Location"]).isEqualTo(longUrl)
    assertThat(redirectResponse.body.toUtf8()).isEmpty()
  }

  @Test
  fun malformedLongUrl() {
    val longUrl = ""
    assertFailsWith<BadRequestException> {
      createShortUrlWebAction.createShortUrl(CreateShortUrlWebAction.Request(longUrl))
    }
  }

  @Test
  fun unknownToken() {
    assertFailsWith<NotFoundException> {
      shortUrlWebAction.follow("unknown")
    }
  }

  private fun ResponseBody.toUtf8(): String? {
    val buffer = Buffer()
    writeTo(buffer)
    return buffer.readUtf8()
  }
}
