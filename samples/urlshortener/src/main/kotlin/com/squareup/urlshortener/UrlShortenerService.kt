package com.squareup.urlshortener

import misk.MiskApplication

fun main(args: Array<String>) {
  MiskApplication(UrlShortenerServiceModule()).run(args)
}