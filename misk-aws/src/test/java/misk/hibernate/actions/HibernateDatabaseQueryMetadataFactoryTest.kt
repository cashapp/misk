package misk.hibernate.actions

import misk.hibernate.Constraint
import misk.hibernate.DbRoot
import misk.hibernate.DbTimestampedEntity
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Query
import misk.web.metadata.Field
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.containsExactly
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Table

class HibernateDatabaseQueryMetadataFactoryTest {
  val converter = HibernateDatabaseQueryMetadataFactory()
  @Test
  fun happy () {
    val metadata = converter.fromQuery(DbMovie::class, MovieQuery::class)
    assertThat(metadata.allowedCapabilities).isEmpty()
    assertThat(metadata.allowedServices).isEmpty()
    assertThat(metadata.accessAnnotation).isNull()
    assertThat(metadata.table).isEqualTo("movies")
    assertThat(metadata.entityClass).isEqualTo(DbMovie::class.simpleName)
    assertThat(metadata.queryClass).isEqualTo(MovieQuery::class.simpleName)
    assertThat(metadata.constraints).containsExactly(
        "nameEq" to Field(name = "name", type = "String", repeated = false),
        "releaseDateBefore" to Field(name = "date", type = "java.time.LocalDate", repeated = false),
        "id" to Field(name = "id", type = "Long", repeated = false)
    )
//    assertThat(metadata.orders).isEqualTo()
//    assertThat(metadata.selects).isEqualTo()
  }



  @Entity
  @misk.hibernate.annotation.Keyspace("movies")
  @Table(name = "movies")
  class DbMovie() : DbRoot<DbMovie>, DbTimestampedEntity {
    @javax.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override lateinit var id: Id<DbMovie>

    @Column
    override lateinit var updated_at: Instant

    @Column
    override lateinit var created_at: Instant

    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = true)
    var release_date: LocalDate? = null

    constructor(name: String, releaseDate: LocalDate? = null) : this() {
      this.name = name
      this.release_date = releaseDate
    }
  }

  interface MovieQuery : Query<DbMovie> {
    @Constraint("name")
    fun nameEq(name: String): MovieQuery

    @Constraint(path = "release_date", operator = Operator.LT)
    fun releaseDateBefore(date: LocalDate): MovieQuery

    @Constraint(path = "id", operator = Operator.EQ)
    fun id(id: Id<DbMovie>): MovieQuery
  }
}