package misk.hibernate

import java.util.Date
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType.IDENTITY
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "movies")
class DbMovie() {
  @Id
  @GeneratedValue(strategy = IDENTITY)
  var id: Long? = null

  var name: String? = null

  var created_at: Date? = null

  constructor(name: String, createdAt: Date) : this() {
    this.name = name
    this.created_at = createdAt
  }

  companion object {
    // TODO(jwilson): move this to resources
    val CREATE_TABLE_MOVIES = """
        |CREATE TABLE movies(
        |  id bigint(20) NOT NULL AUTO_INCREMENT,
        |  name varchar(255) NOT NULL,
        |  created_at timestamp NOT NULL
        |)
        |""".trimMargin()
  }
}