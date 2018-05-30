package misk.hibernate

import java.util.Date
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

@Entity
@Table(name = "movies")
class DbMovie() : DbEntity<DbMovie> {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbMovie>

  var name: String? = null

  var created_at: Date? = null

  constructor(name: String, createdAt: Date) : this() {
    this.name = name
    this.created_at = createdAt
  }
}