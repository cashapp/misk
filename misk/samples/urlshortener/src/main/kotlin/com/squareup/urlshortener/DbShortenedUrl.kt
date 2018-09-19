package com.squareup.urlshortener

import misk.hibernate.DbUnsharded
import misk.hibernate.Id
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

@Entity
@Table(name = "shortened_urls")
// TODO we really should shard this sample
class DbShortenedUrl() : DbUnsharded<DbShortenedUrl> {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbShortenedUrl>

  @Column(nullable = false)
  lateinit var long_url: String

  @Column(nullable = false)
  lateinit var token: UrlToken

  constructor(long_url: String, token: UrlToken) : this() {
    this.long_url = long_url
    this.token = token
  }
}