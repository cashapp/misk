package misk.hibernate.actions

import misk.hibernate.Constraint
import misk.hibernate.DbRoot
import misk.hibernate.DbTimestampedEntity
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Order
import misk.hibernate.Projection
import misk.hibernate.Property
import misk.hibernate.Query
import misk.hibernate.Select
import misk.hibernate.Session
import misk.hibernate.actions.HibernateDatabaseQueryAction.Companion.HIBERNATE_QUERY_WEBACTION_PATH
import misk.web.metadata.DatabaseQueryMetadata
import misk.web.metadata.Field
import misk.web.metadata.Type
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

class HibernateDatabaseQueryMetadataFactoryTest {
  val converter = HibernateDatabaseQueryMetadataFactory(
      listOf()
  )

  @Test
  fun happy() {
    val metadata = converter.fromQuery(DbMovie::class, MovieQuery::class)
    assertThat(metadata.queryWebActionPath).isEqualTo(HIBERNATE_QUERY_WEBACTION_PATH)
    assertThat(metadata.allowedCapabilities).isEmpty()
    assertThat(metadata.allowedServices).isEmpty()
    assertThat(metadata.accessAnnotation).isNull()
    assertThat(metadata.table).isEqualTo("movies")
    assertThat(metadata.entityClass).isEqualTo(DbMovie::class.simpleName)
    assertThat(metadata.queryClass).isEqualTo(MovieQuery::class.simpleName)
    assertThat(metadata.constraints).containsExactly(
        DatabaseQueryMetadata.ConstraintMetadata(name = "name",
            parametersTypeName = "Constraint/MovieQuery/name", path = "name", operator = "EQ"),
        DatabaseQueryMetadata.ConstraintMetadata(name = "releaseDateIsNull",
            parametersTypeName = "Constraint/MovieQuery/releaseDateIsNull", path = "release_date",
            operator = "IS_NULL"),
        DatabaseQueryMetadata.ConstraintMetadata(name = "releaseDateLessThan",
            parametersTypeName = "Constraint/MovieQuery/releaseDateLessThan", path = "release_date",
            operator = "LT")
    )
    assertThat(metadata.orders).containsExactly(
        DatabaseQueryMetadata.OrderMetadata(name = "releaseDateAsc",
            parametersTypeName = "Order/MovieQuery/releaseDateAsc", path = "release_date",
            ascending = true),
        DatabaseQueryMetadata.OrderMetadata(name = "releaseDateDesc",
            parametersTypeName = "Order/MovieQuery/releaseDateDesc", path = "release_date",
            ascending = false)
    )
    assertThat(metadata.selects).containsExactly(
        DatabaseQueryMetadata.SelectMetadata(name = "listAsNameAndReleaseDate",
            parametersTypeName = "Select/MovieQuery/listAsNameAndReleaseDate", paths = listOf()),
        DatabaseQueryMetadata.SelectMetadata(name = "listAsNames",
            parametersTypeName = "Select/MovieQuery/listAsNames", paths = listOf("name")),
        DatabaseQueryMetadata.SelectMetadata(name = "uniqueName",
            parametersTypeName = "Select/MovieQuery/uniqueName", paths = listOf("name")),
    )
    assertThat(metadata.types).containsAllEntriesOf(mapOf(
        "Constraint/MovieQuery/name" to Type(
            fields = listOf(Field(name = "name", type = "String", repeated = false))),
        "Constraint/MovieQuery/releaseDateIsNull" to Type(
            fields = listOf(Field(name = "Add Constraint", type = "Boolean", repeated = false))),
        "Constraint/MovieQuery/releaseDateLessThan" to Type(
            fields = listOf(Field(name = "upperBound", type = "LocalDate", repeated = false))),
        "Order/MovieQuery/releaseDateAsc" to Type(
            fields = listOf(Field(name = "Add Order", type = "Boolean", repeated = false))),
        "Order/MovieQuery/releaseDateDesc" to Type(
            fields = listOf(Field(name = "Add Order", type = "Boolean", repeated = false))),
        "Select/MovieQuery/listAsNameAndReleaseDate" to Type(
            fields = listOf(Field(name = "Add Select", type = "Boolean", repeated = false))),
        "Select/MovieQuery/listAsNames" to Type(
            fields = listOf(Field(name = "Add Select", type = "Boolean", repeated = false))),
        "Select/MovieQuery/uniqueName" to Type(
            fields = listOf(Field(name = "Add Select", type = "Boolean", repeated = false))),
        "queryType" to Type(fields = listOf(
            Field(name = "Constraint/MovieQuery/name", type = "Constraint/MovieQuery/name", repeated = false),
            Field(name = "Constraint/MovieQuery/releaseDateIsNull", type = "Constraint/MovieQuery/releaseDateIsNull",
                repeated = false),
            Field(name = "Constraint/MovieQuery/releaseDateLessThan", type = "Constraint/MovieQuery/releaseDateLessThan",
                repeated = false),
            Field(name = "Order/MovieQuery/releaseDateAsc", type = "Order/MovieQuery/releaseDateAsc",
                repeated = false),
            Field(name = "Order/MovieQuery/releaseDateDesc", type = "Order/MovieQuery/releaseDateDesc",
                repeated = false),
            Field(name = "Select/MovieQuery/listAsNameAndReleaseDate",
                type = "Select/MovieQuery/listAsNameAndReleaseDate", repeated = false),
            Field(name = "Select/MovieQuery/listAsNames", type = "Select/MovieQuery/listAsNames",
                repeated = false),
            Field(name = "Select/MovieQuery/uniqueName", type = "Select/MovieQuery/uniqueName", repeated = false),
        )),
    ))
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
    @Constraint(path = "name")
    fun name(name: String): MovieQuery

    @Constraint(path = "release_date", operator = Operator.LT)
    fun releaseDateLessThan(upperBound: LocalDate?): MovieQuery

    @Constraint(path = "release_date", operator = Operator.IS_NULL)
    fun releaseDateIsNull(): MovieQuery

    @Order(path = "release_date")
    fun releaseDateAsc(): MovieQuery

    @Order(path = "release_date", asc = false)
    fun releaseDateDesc(): MovieQuery

    @Select
    fun listAsNameAndReleaseDate(session: Session): List<NameAndReleaseDate>

    @Select("name")
    fun uniqueName(session: Session): String?

    @Select("name")
    fun listAsNames(session: Session): List<String>
  }

  data class NameAndReleaseDate(
    @Property("name") var name: String,
    @Property("release_date") var releaseDate: LocalDate?
  ) : Projection
}