package com.squareup.urlshortener

import okhttp3.HttpUrl

interface UrlStore {
  fun urlToToken(longUrl: HttpUrl): UrlToken
  fun tokenToUrl(token: UrlToken): HttpUrl?
}