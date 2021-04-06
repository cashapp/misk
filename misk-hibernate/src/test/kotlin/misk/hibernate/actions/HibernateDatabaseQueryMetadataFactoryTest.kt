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
import misk.hibernate.actions.HibernateDatabaseQueryDynamicAction.Companion.HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH
import misk.hibernate.actions.HibernateDatabaseQueryStaticAction.Companion.HIBERNATE_QUERY_STATIC_WEBACTION_PATH
import misk.web.MiskWebFormBuilder.Field
import misk.web.MiskWebFormBuilder.Type
import misk.web.metadata.database.DatabaseQueryMetadata
import misk.web.metadata.database.NoAdminDashboardDatabaseAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Table

class HibernateDatabaseQueryMetadataFactoryTest {
  private val converter = HibernateDatabaseQueryMetadataFactory(listOf())

  @Test
  fun `static happy path`() {
    val metadata =
      converter.fromQuery(DbMovie::class, MovieQuery::class, NoAdminDashboardDatabaseAccess::class)
    assertThat(metadata.queryWebActionPath).isEqualTo(HIBERNATE_QUERY_STATIC_WEBACTION_PATH)
    assertThat(metadata.allowedCapabilities).isEmpty()
    assertThat(metadata.allowedServices).isEmpty()
    assertThat(metadata.accessAnnotation).isEqualTo(
      NoAdminDashboardDatabaseAccess::class.simpleName!!
    )
    assertThat(metadata.table).isEqualTo("movies")
    assertThat(metadata.entityClass).isEqualTo(DbMovie::class.simpleName)
    assertThat(metadata.queryClass).isEqualTo(MovieQuery::class.simpleName)
    assertThat(metadata.constraints).containsExactly(
      DatabaseQueryMetadata.ConstraintMetadata(
        name = "name",
        parametersTypeName = "Constraint/MovieQuery/name", path = "name", operator = "EQ"
      ),
      DatabaseQueryMetadata.ConstraintMetadata(
        name = "releaseDateIsNull",
        parametersTypeName = "Constraint/MovieQuery/releaseDateIsNull", path = "release_date",
        operator = "IS_NULL"
      ),
      DatabaseQueryMetadata.ConstraintMetadata(
        name = "releaseDateLessThan",
        parametersTypeName = "Constraint/MovieQuery/releaseDateLessThan", path = "release_date",
        operator = "LT"
      )
    )
    assertThat(metadata.orders).containsExactly(
      DatabaseQueryMetadata.OrderMetadata(
        name = "releaseDateAsc",
        parametersTypeName = "Order/MovieQuery/releaseDateAsc", path = "release_date",
        ascending = true
      ),
      DatabaseQueryMetadata.OrderMetadata(
        name = "releaseDateDesc",
        parametersTypeName = "Order/MovieQuery/releaseDateDesc", path = "release_date",
        ascending = false
      )
    )
    assertThat(metadata.selects).containsExactly(
      DatabaseQueryMetadata.SelectMetadata(
        name = "listAsNameAndReleaseDate",
        parametersTypeName = "Select/MovieQuery/listAsNameAndReleaseDate", paths = listOf()
      ),
      DatabaseQueryMetadata.SelectMetadata(
        name = "listAsNames",
        parametersTypeName = "Select/MovieQuery/listAsNames", paths = listOf("name")
      ),
      DatabaseQueryMetadata.SelectMetadata(
        name = "uniqueName",
        parametersTypeName = "Select/MovieQuery/uniqueName", paths = listOf("name")
      ),
    )
    assertThat(metadata.types).containsExactlyEntriesOf(
      mapOf(
        "Config/Query" to Type(
          fields = listOf(
            Field(
              name = "maxRows",
              repeated = false,
              type = "Int"
            )
          )
        ),
        "Constraint/MovieQuery/name" to Type(
          fields = listOf(Field(name = "name", type = "String", repeated = false))
        ),
        "Constraint/MovieQuery/releaseDateIsNull" to Type(
          fields = listOf(Field(name = "Add Constraint", type = "Boolean", repeated = false))
        ),
        "Constraint/MovieQuery/releaseDateLessThan" to Type(
          fields = listOf(Field(name = "upperBound", type = "LocalDate", repeated = false))
        ),
        "Order/MovieQuery/releaseDateAsc" to Type(
          fields = listOf(
            Field(
              name = "Add Order (path=release_date, asc=true)",
              type = "Boolean",
              repeated = false
            )
          )
        ),
        "Order/MovieQuery/releaseDateDesc" to Type(
          fields = listOf(
            Field(
              name = "Add Order (path=release_date, asc=false)",
              type = "Boolean",
              repeated = false
            )
          )
        ),
        "Select/MovieQuery/listAsNameAndReleaseDate" to Type(
          fields = listOf(Field(name = "Add Select (paths=[])", type = "Boolean", repeated = false))
        ),
        "Select/MovieQuery/listAsNames" to Type(
          fields = listOf(
            Field(
              name = "Add Select (paths=[name])",
              type = "Boolean",
              repeated = false
            )
          )
        ),
        "Select/MovieQuery/uniqueName" to Type(
          fields = listOf(
            Field(
              name = "Add Select (paths=[name])",
              type = "Boolean",
              repeated = false
            )
          )
        ),
        "queryType" to Type(
          fields = listOf(
            Field(name = "Config/Query", type = "Config/Query", repeated = false),
            Field(
              name = "Constraint/MovieQuery/name",
              type = "Constraint/MovieQuery/name",
              repeated = false
            ),
            Field(
              name = "Constraint/MovieQuery/releaseDateIsNull",
              type = "Constraint/MovieQuery/releaseDateIsNull",
              repeated = false
            ),
            Field(
              name = "Constraint/MovieQuery/releaseDateLessThan",
              type = "Constraint/MovieQuery/releaseDateLessThan",
              repeated = false
            ),
            Field(
              name = "Order/MovieQuery/releaseDateAsc", type = "Order/MovieQuery/releaseDateAsc",
              repeated = false
            ),
            Field(
              name = "Order/MovieQuery/releaseDateDesc", type = "Order/MovieQuery/releaseDateDesc",
              repeated = false
            ),
            Field(
              name = "Select/MovieQuery/listAsNameAndReleaseDate",
              type = "Select/MovieQuery/listAsNameAndReleaseDate", repeated = false
            ),
            Field(
              name = "Select/MovieQuery/listAsNames", type = "Select/MovieQuery/listAsNames",
              repeated = false
            ),
            Field(
              name = "Select/MovieQuery/uniqueName",
              type = "Select/MovieQuery/uniqueName",
              repeated = false
            ),
          )
        ),
      )
    )
  }

  @Test
  fun `dynamic happy path`() {
    val metadata = converter.fromQuery(
      DbMovie::class,
      null,
      NoAdminDashboardDatabaseAccess::class
    )
    assertThat(metadata.queryWebActionPath).isEqualTo(HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH)
    assertThat(metadata.allowedCapabilities).isEmpty()
    assertThat(metadata.allowedServices).isEmpty()
    assertThat(metadata.accessAnnotation).isEqualTo(
      NoAdminDashboardDatabaseAccess::class.simpleName!!
    )
    assertThat(metadata.table).isEqualTo("movies")
    assertThat(metadata.entityClass).isEqualTo(DbMovie::class.simpleName)
    assertThat(metadata.queryClass).isEqualTo("DbMovieDynamicQuery")
    assertThat(metadata.constraints).isEmpty()
    assertThat(metadata.orders).isEmpty()
    assertThat(metadata.selects).isEmpty()
    assertThat(metadata.types).containsExactlyEntriesOf(
      mapOf(
        "Config/Query" to Type(
          fields = listOf(
            Field(
              name = "maxRows",
              repeated = false,
              type = "Int"
            )
          )
        ),
        "Constraint/Dynamic" to Type(
          fields = listOf(
            Field(
              name = "path",
              type = "Enum<DbMoviePaths,created_at,id,name,release_date,updated_at,gid,rootId>",
              repeated = false
            ),
            Field(
              name = "operator",
              type = "Enum<misk.hibernate.Operator,LT,LE,EQ,EQ_OR_IS_NULL,GE,GT,NE,IN,NOT_IN,IS_NOT_NULL," +
                "IS_NULL>",
              repeated = false
            ),
            Field(name = "value", type = "String", repeated = false),
          )
        ),
        "Order/Dynamic" to Type(
          fields = listOf(
            Field(
              name = "path",
              type = "Enum<DbMoviePaths,created_at,id,name,release_date,updated_at,gid,rootId>",
              repeated = false
            ),
            Field(name = "ascending", type = "Boolean", repeated = false)
          )
        ),
        "Select/Dynamic" to Type(
          fields = listOf(
            Field(
              name = "paths",
              type = "Enum<DbMoviePaths,created_at,id,name,release_date,updated_at,gid,rootId>",
              repeated = true
            )
          )
        ),
        "queryType" to Type(
          fields = listOf(
            Field(name = "queryConfig", type = "Config/Query", repeated = false),
            Field(name = "constraints", type = "Constraint/Dynamic", repeated = true),
            Field(name = "orders", type = "Order/Dynamic", repeated = true),
            Field(name = "select", type = "Select/Dynamic", repeated = false),
          )
        ),
      )
    )
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
