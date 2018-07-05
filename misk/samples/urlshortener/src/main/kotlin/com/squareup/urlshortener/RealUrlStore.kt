package com.squareup.urlshortener

import misk.hibernate.Constraint
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.tokens.TokenGenerator
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealUrlStore : UrlStore {
  @Inject @UrlShortener lateinit var transacter: Transacter
  @Inject lateinit var tokenGenerator: TokenGenerator
  @Inject lateinit var queryFactory: Query.Factory

  override fun urlToToken(longUrl: HttpUrl): UrlToken {
    val token = UrlToken(tokenGenerator.generate(label = "url", length = 6))

    // TODO(jwilson): recover from collision with persisted tokens.
    transacter.transaction { session ->
      session.save(DbShortenedUrl(longUrl.toString(), token))
    }

    return token
  }

  override fun tokenToUrl(token: UrlToken): HttpUrl? {
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

  interface ShortenedUrlQuery : Query<DbShortenedUrl> {
    @Constraint("token")
    fun token(token: UrlToken): ShortenedUrlQuery
  }
}
