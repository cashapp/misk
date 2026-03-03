package misk.hibernate

import java.time.Instant
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Table

@Entity
@Table(name = "actors")
class DbActor() : DbRoot<DbActor>, DbTimestampedEntity {
  @javax.persistence.Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  override lateinit var id: Id<DbActor>

  @Column
  override lateinit var updated_at: Instant

  @Column
  override lateinit var created_at: Instant

  @Column(nullable = false)
  lateinit var name: String

  @Column
  var birth_date: LocalDate? = null

  constructor(name: String, birthDate: LocalDate? = null) : this() {
    this.name = name
    this.birth_date = birthDate
  }
}
