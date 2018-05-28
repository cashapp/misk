package misk.hibernate

import java.util.Date
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "movies")
class DbMovie() {
  @Id
  @GeneratedValue
  var id: Long? = null

  var name: String? = null

  var created_at: Date? = null

  constructor(name: String, createdAt: Date) : this() {
    this.name = name
    this.created_at = createdAt
  }
}