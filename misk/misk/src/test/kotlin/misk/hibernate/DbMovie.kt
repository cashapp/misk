package misk.hibernate

import java.time.Instant
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

@Entity
@Table(name = "movies")
class DbMovie() : DbEntity<DbMovie> {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbMovie>

  @Column(nullable = false)
  lateinit var name: String

  @Column(nullable = false)
  var release_date: LocalDate? = null

  @Column(nullable = false)
  lateinit var created_at: Instant

  constructor(name: String, releaseDate: LocalDate?, createdAt: Instant) : this() {
    this.name = name
    this.release_date = releaseDate
    this.created_at = createdAt
  }
}