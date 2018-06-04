package com.squareup.urlshortener

import okhttp3.HttpUrl

interface UrlStore {
  fun urlToToken(longUrl: HttpUrl): String
  fun tokenToUrl(token: String): HttpUrl?
}