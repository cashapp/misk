package com.squareup.urlshortener

import misk.config.Config

data class EndpointConfig(
  val base_url: String
) : Config
