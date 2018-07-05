package com.squareup.urlshortener

/**
 * A short (6-character) token that can be exchanged for a long URL. Because this is a boxed string
 * it can be directly persisted to Hibernate.
 */
data class UrlToken(val token: String)