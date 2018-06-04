package com.squareup.urlshortener

import misk.hibernate.Constraint
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import okhttp3.HttpUrl
import okio.ByteString
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealUrlStore : UrlStore {
  @Inject @UrlShortener lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  val random = SecureRandom()

  override fun urlToToken(longUrl: HttpUrl): String {
    val token = generateRandomToken()

    // TODO(jwilson): recover from collision with persisted tokens.
    transacter.transaction { session ->
      session.save(DbShortenedUrl(longUrl.toString(), token))
    }

    return token
  }

  override fun tokenToUrl(token: String): HttpUrl? {
    return transacter.transaction { session ->
      val shortenedUrl = queryFactory.newQuery<ShortenedUrlQuery>()
          .token(token)
          .uniqueResult(session)
      when (shortenedUrl) {
        null -> null
        else -> HttpUrl.parse(shortenedUrl.long_url)
      }
    }
  }

  // TODO(jwilson): misk should provide a token generator.
  fun generateRandomToken(): String {
    val bytes = ByteArray(6)
    random.nextBytes(bytes)
    return ByteString.of(*bytes).base64Url()
  }

  interface ShortenedUrlQuery : Query<DbShortenedUrl> {
    @Constraint("token")
    fun token(name: String): ShortenedUrlQuery
  }
}
