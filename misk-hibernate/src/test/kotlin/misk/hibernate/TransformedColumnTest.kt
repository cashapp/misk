package misk.hibernate

import misk.MiskTestingServiceModule
import misk.client.HttpClientsConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.config.ConfigSource
import wisp.config.WispConfig
import wisp.config.addWispConfigSources
import wisp.deployment.TESTING
import java.io.Serializable
import java.util.Objects
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.AttributeConverter
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

interface SwappableTransformer {
  fun assemble(ctx: TransformerContext, owner: Any?, value: Serializable): Any

  fun disassemble(ctx: TransformerContext, value: Any): Serializable
}

class DelegatingTransformer(private val ctx: TransformerContext) : Transformer(ctx) {
  @Inject
  lateinit var transformer: SwappableTransformer

  override fun assemble(owner: Any?, value: Serializable): Any = transformer.assemble(
    ctx,
    owner,
    value
  )

  override fun disassemble(value: Any): Serializable = transformer.disassemble(ctx, value)
}

@MiskTest(startService = true)
class TransformedColumnTest {

  @Inject
  @TransformedColumnTestDb
  lateinit var transacter: Transacter

  @Inject
  lateinit var queryFactory: Query.Factory

  var swappableTransformer: SwappableTransformer? = null

  private fun withTransformer(
    assemble: (TransformerContext, Any?, Serializable) -> Any,
    disassemble: (TransformerContext, Any) -> Serializable,
    f: () -> Unit
  ) {
    val saved = swappableTransformer

    swappableTransformer = object : SwappableTransformer {
      override fun assemble(ctx: TransformerContext, owner: Any?, value: Serializable) = assemble(
        ctx,
        owner,
        value
      )

      override fun disassemble(ctx: TransformerContext, value: Any) = disassemble(ctx, value)
    }

    try {
      f()
    } finally {
      swappableTransformer = saved
    }
  }

  @Test
  fun testBasicIncrement() {
    val assemble = { _: TransformerContext, _: Any?, value: Serializable ->
      when (value) {
        is Int -> value - 2
        else -> value
      }
    }

    val disassemble = { _: TransformerContext, value: Any ->
      when (value) {
        is Int -> value + 2
        else -> value as Serializable
      }
    }

    withTransformer(assemble, disassemble) {
      transacter.transaction { session ->
        session.save(DbManyTypes(1, 1.2, "Test", "Bytes".toByteArray()))
      }
      transacter.transaction { session ->
        session.hibernateSession.sessionFactory.sessionFactoryOptions.criteriaLiteralHandlingMode

        val rows = queryFactory.newQuery<ManyTypesRawQuery>()
          .allowTableScan()
          .list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(3)
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesQuery>()
          .allowTableScan()
          .list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(1)
      }
    }
  }

  @Test
  fun testMultipleTypes() {
    val assemble = { ctx: TransformerContext, _: Any?, value: Serializable ->
      when (ctx.columnName) {
        "int_field" -> value as Int - 2
        "double_field" -> value as Double - 2.0
        else -> value
      }
    }

    val disassemble = { ctx: TransformerContext, value: Any ->
      when (ctx.columnName) {
        "int_field" -> value as Int + 2
        "double_field" -> value as Double + 2.0
        else -> value as Serializable
      }
    }

    withTransformer(assemble, disassemble) {
      transacter.transaction { session ->
        session.save(DbManyTypes(1, 1.2, "Test", "Bytes".toByteArray()))
      }
      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesRawQuery>()
          .allowTableScan()
          .list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(3)
        assertThat(rows[0].doubleField).isCloseTo(3.2, Percentage.withPercentage(0.1))
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesQuery>()
          .allowTableScan()
          .list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(1)
        assertThat(rows[0].doubleField).isCloseTo(1.2, Percentage.withPercentage(0.1))
      }
    }
  }

  @Test
  fun testTransformedStringQuery() {

    val suffix = "_modified"
    val assemble = { _: TransformerContext, _: Any?, value: Serializable ->
      when (value) {
        is String -> value.removeSuffix(suffix)
        else -> value
      }
    }

    val disassemble = { _: TransformerContext, value: Any ->
      when (value) {
        is String -> "$value$suffix"
        else -> value as Serializable
      }
    }

    withTransformer(assemble, disassemble) {
      transacter.transaction { session ->
        session.save(DbManyTypes(1, 1.2, "Test", "Bytes".toByteArray()))
        session.hibernateSession.clear()
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesProjectionQuery>()
          .allowTableScan()
          .stringField("Test")
          .query(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].stringField).isEqualTo("Test")
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesRawQuery>()
          .allowTableScan()
          .stringField("Test_modified")
          .list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].stringField).isEqualTo("Test$suffix")
      }
    }
  }

  @Test
  fun testTransformedQuery() {
    val assemble = { ctx: TransformerContext, _: Any?, value: Serializable ->
      when (ctx.columnName) {
        "int_field" -> value as Int - 2
        else -> value
      }
    }

    val disassemble = { ctx: TransformerContext, value: Any ->
      when (ctx.columnName) {
        "int_field" -> value as Int + 2
        else -> value as Serializable
      }
    }

    withTransformer(assemble, disassemble) {
      transacter.transaction { session ->
        session.save(DbManyTypes(1, 1.2, "Test", "Bytes".toByteArray()))
        session.hibernateSession.clear()
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesProjectionQuery>()
          .allowTableScan()
          .intField(1)
          .query(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(1)
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesRawQuery>()
          .allowTableScan()
          .intField(3)
          .list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(3)
      }
    }
  }

  @Test
  fun testTransformerArguments() {
    val assemble = { ctx: TransformerContext, _: Any?, value: Serializable ->
      when (ctx.columnName) {
        "int_field" -> (value as Int) - (ctx.arguments["amount"] as Int)
        else -> value
      }
    }

    val disassemble = { ctx: TransformerContext, value: Any ->
      when (ctx.columnName) {
        "int_field" -> (value as Int) + (ctx.arguments["amount"] as Int)
        else -> value as Serializable
      }
    }

    withTransformer(assemble, disassemble) {
      val value = 1

      transacter.transaction { session ->
        session.save(DbManyTypes(value, 1.2, "Test", "Bytes".toByteArray()))
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesProjectionQuery>()
          .allowTableScan()
          .intField(value)
          .list(session)
        assertThat(rows).hasSize(1)
        assertThat(rows.first().intField).isEqualTo(value)
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesRawQuery>()
          .allowTableScan()
          .list(session)

        val annotationForInt = DbManyTypes::class.declaredMemberProperties
          .mapNotNull { prop -> prop.javaField?.getAnnotation(TransformedInt::class.java) }
          .first()
        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(value + annotationForInt.amount)
      }
    }
  }

  interface ManyTypesQuery : Query<DbManyTypes> {
    @Constraint(path = "intField")
    fun intField(intField: Int): ManyTypesQuery

    @Constraint(path = "doubleField")
    fun doubleField(doubleField: Double): ManyTypesQuery

    @Constraint(path = "stringField")
    fun stringField(stringField: String): ManyTypesQuery

    @Constraint(path = "byteArrayField")
    fun byteArrayField(byteArrayField: ByteArray): ManyTypesQuery
  }

  interface ManyTypesRawQuery : Query<DbManyTypesRaw> {
    @Constraint(path = "intField")
    fun intField(intField: Int): ManyTypesRawQuery

    @Constraint(path = "stringField")
    fun stringField(stringField: String): ManyTypesRawQuery
  }

  interface ManyTypesProjectionQuery : Query<DbManyTypes> {
    @Constraint(path = "intField")
    fun intField(intField: Int): ManyTypesProjectionQuery

    @Constraint(path = "doubleField")
    fun doubleField(doubleField: Double): ManyTypesProjectionQuery

    @Constraint(path = "stringField")
    fun stringField(stringField: String): ManyTypesProjectionQuery

    @Constraint(path = "byteArrayField")
    fun byteArrayField(byteArrayField: ByteArray): ManyTypesProjectionQuery

    @Select
    fun query(session: Session): List<ManyTypesProjection>
  }

  @Target(AnnotationTarget.FIELD)
  @TransformedType(transformer = DelegatingTransformer::class, targetType = Int::class)
  annotation class TransformedInt(val amount: Int = 0)

  @Target(AnnotationTarget.FIELD)
  @TransformedType(transformer = DelegatingTransformer::class, targetType = Double::class)
  annotation class TransformedDouble

  @Target(AnnotationTarget.FIELD)
  @TransformedType(transformer = DelegatingTransformer::class, targetType = String::class)
  annotation class TransformedString

  @Target(AnnotationTarget.FIELD)
  @TransformedType(transformer = DelegatingTransformer::class, targetType = ByteArray::class)
  annotation class TransformedByteArray

  class Bumper : AttributeConverter<Int, Int> {
    override fun convertToDatabaseColumn(attribute: Int): Int = attribute + 2
    override fun convertToEntityAttribute(dbData: Int): Int = dbData - 2
  }

  @Entity
  @Table(name = "manytypes")
  class DbManyTypes : DbUnsharded<DbManyTypes> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbManyTypes>

    @Column(name = "int_field")
    @TransformedInt(amount = 2)
    @Convert(converter = Bumper::class)
    var intField: Int = 0

    @Column(name = "double_field")
    @TransformedDouble
    var doubleField: Double = 0.0

    @Column(name = "string_field")
    @TransformedString
    var stringField: String = ""

    @Column(name = "byte_array_field", nullable = false)
    @TransformedByteArray
    var byteArrayField: ByteArray = byteArrayOf()

    constructor(
      intField: Int,
      doubleField: Double,
      stringField: String,
      byteArrayField: ByteArray
    ) {
      this.intField = intField
      this.doubleField = doubleField
      this.stringField = stringField
      this.byteArrayField = byteArrayField
    }
  }

  @Entity
  @Table(name = "manytypes")
  class DbManyTypesRaw : DbUnsharded<DbManyTypesRaw> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbManyTypesRaw>

    @Column(name = "int_field")
    var intField: Int = 0

    @Column(name = "double_field")
    var doubleField: Double = 0.0

    @Column(name = "string_field")
    var stringField: String = ""

    @Column(name = "byte_array_field")
    var byteArrayField: ByteArray = byteArrayOf()

    constructor(
      intField: Int,
      doubleField: Double,
      stringField: String,
      byteArrayField: ByteArray
    ) {
      this.intField = intField
      this.doubleField = doubleField
      this.stringField = stringField
      this.byteArrayField = byteArrayField
    }
  }

  data class ManyTypesProjection(
    @Property("intField") val intField: Int,
    @Property("doubleField") val doubleField: Double,
    @Property("stringField") val stringField: String,
    @Property("byteArrayField") val byteArrayField: ByteArray
  ) : Projection {
    override fun hashCode(): Int = Objects.hash(intField, doubleField, stringField, byteArrayField)
    override fun equals(other: Any?): Boolean {
      if (other == null) {
        return false
      }
      if (other !is ManyTypesProjection) {
        return false
      }
      return intField == other.intField &&
        doubleField == other.doubleField &&
        stringField == other.stringField &&
        byteArrayField contentEquals other.byteArrayField
    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class TransformedColumnTestDb

  data class TransformedColumnTestConfig(val data_source: DataSourceConfig) : Config

  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))

      bind<SwappableTransformer>().toInstance(object : SwappableTransformer {
        override fun assemble(
          ctx: TransformerContext,
          owner: Any?,
          value: Serializable
        ): Any = swappableTransformer?.assemble(ctx, owner, value)!!

        override fun disassemble(
          ctx: TransformerContext,
          value: Any
        ): Serializable = swappableTransformer?.disassemble(ctx, value)!!
      })

      val conf = WispConfig.builder().addWispConfigSources(
        listOf(
          ConfigSource("classpath:/transformedcolumn-testing.yaml"),
        )
      ).build().loadConfigOrThrow<TransformedColumnTestConfig>()
      install(HibernateTestingModule(TransformedColumnTestDb::class, conf.data_source))
      install(HibernateModule(TransformedColumnTestDb::class, conf.data_source))
      install(object : HibernateEntityModule(TransformedColumnTestDb::class) {
        override fun configureHibernate() {
          addEntities(DbManyTypes::class, DbManyTypesRaw::class)
        }
      })
    }
  }
}
