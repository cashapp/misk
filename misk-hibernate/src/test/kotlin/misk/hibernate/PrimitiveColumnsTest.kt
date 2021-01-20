package misk.hibernate

import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.jdbc.DataSourceConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

@MiskTest
class PrimitiveColumnsTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @PrimitivesDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun happyPath() {
    transacter.transaction { session ->
      session.save(DbPrimitiveTour(false, 9, 8, 7, 6, '5', 4.0f, 3.0))
      session.save(DbPrimitiveTour(true, 2, 3, 4, 5, '6', 7.0f, 8.0))
    }
    transacter.transaction { session ->
      val primitiveTour = queryFactory.newQuery(PrimitiveTourQuery::class)
          .allowTableScan()
          .i1(true)
          .listAsPrimitiveTour(session)
      assertThat(primitiveTour).containsExactly(PrimitiveTour(true, 2, 3, 4, 5, '6', 7.0f, 8.0))
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<RootConfig>("primitivecolumns", Environment.TESTING)
      install(HibernateTestingModule(PrimitivesDb::class, config.data_source))
      install(HibernateModule(PrimitivesDb::class, config.data_source))
      install(object : HibernateEntityModule(PrimitivesDb::class) {
        override fun configureHibernate() {
          addEntities(DbPrimitiveTour::class)
        }
      })
    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class PrimitivesDb

  data class RootConfig(val data_source: DataSourceConfig) : Config

  @Entity
  @Table(name = "primitive_tours")
  class DbPrimitiveTour(
    @Column(nullable = false)
    var i1: Boolean = false,

    @Column(nullable = false)
    var i8: Byte = 0,

    @Column(nullable = false)
    var i16: Short = 0,

    @Column(nullable = false)
    var i32: Int = 0,

    @Column(nullable = false)
    var i64: Long = 0,

    @Column(nullable = false)
    var c16: Char = '\u0000',

    @Column(nullable = false)
    var f32: Float = 0.0f,

    @Column(nullable = false)
    var f64: Double = 0.0

  ) : DbUnsharded<DbPrimitiveTour> {
    @javax.persistence.Id @GeneratedValue override lateinit var id: Id<DbPrimitiveTour>
  }

  data class PrimitiveTour(
    @Property("i1") var i1: Boolean,
    @Property("i8") var i8: Byte,
    @Property("i16") var i16: Short,
    @Property("i32") var i32: Int,
    @Property("i64") var i64: Long,
    @Property("c16") var c16: Char,
    @Property("f32") var f32: Float,
    @Property("f64") var f64: Double
  ) : Projection

  interface PrimitiveTourQuery : Query<DbPrimitiveTour> {
    @Constraint(path = "i1")
    fun i1(i1: Boolean): PrimitiveTourQuery

    @Select
    fun listAsPrimitiveTour(session: Session): List<PrimitiveTour>
  }
}
