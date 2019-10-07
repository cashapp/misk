package misk.hibernate

import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import java.io.Serializable
import java.util.Arrays
import java.util.Objects
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

interface SwappableTransformer {
  fun assemble(ctx: TransformerContext, owner: Any?, value: Serializable): Any

  fun disassemble(ctx: TransformerContext, value: Any): Serializable
}

class DelegatingTransformer(val ctx: TransformerContext) : Transformer(ctx) {
  @Inject
  lateinit var transformer: SwappableTransformer

  override fun assemble(owner: Any?, value: Serializable): Any = transformer.assemble(ctx, owner, value)
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

  fun withTransformer(xformer: SwappableTransformer, f: () -> Unit) {
    val saved = swappableTransformer
    swappableTransformer = xformer
    f()
    swappableTransformer = saved
  }

  @Test
  fun testBasicIncrement() {
    val incrementIntTransformer = object : SwappableTransformer {
      override fun assemble(ctx: TransformerContext, owner: Any?, value: Serializable): Any = when (ctx.columnName) {
        "int_field" -> value as Int - 2
        else -> value
      }

      override fun disassemble(ctx: TransformerContext, value: Any): Serializable = when (ctx.columnName) {
        "int_field" -> value as Int + 2
        else -> value as Serializable
      }
    }

    withTransformer(incrementIntTransformer) {
      transacter.transaction { session ->
        session.save(DbManyTypes(1, 1.2, "Test", "Bytes".toByteArray()))
      }
      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesRawQuery>().list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(3)
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesQuery>().list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(1)

      }
    }
  }

  @Test
  fun testMultipleTypes() {
    val incrementIntTransformer = object : SwappableTransformer {
      override fun assemble(ctx: TransformerContext, owner: Any?, value: Serializable): Any = when (ctx.columnName) {
        "int_field" -> value as Int - 2
        "double_field" -> value as Double - 2.0
        else -> value
      }

      override fun disassemble(ctx: TransformerContext, value: Any): Serializable = when (ctx.columnName) {
        "int_field" -> value as Int + 2
        "double_field" -> value as Double + 2.0
        else -> value as Serializable
      }
    }

    withTransformer(incrementIntTransformer) {
      transacter.transaction { session ->
        session.save(DbManyTypes(1, 1.2, "Test", "Bytes".toByteArray()))
      }
      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesRawQuery>().list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(3)
        assertThat(rows[0].doubleField).isCloseTo(3.2, Percentage.withPercentage(0.1))
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesQuery>().list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(1)
        assertThat(rows[0].doubleField).isCloseTo(1.2, Percentage.withPercentage(0.1))
      }
    }
  }

  @Test
  fun testTransformedQuery() {
    val incrementIntTransformer = object : SwappableTransformer {
      override fun assemble(ctx: TransformerContext, owner: Any?, value: Serializable): Any {
        return when (ctx.columnName) {
          "int_field" -> value as Int - 2
          else -> value
        }
      }

      override fun disassemble(ctx: TransformerContext, value: Any): Serializable {
        return when (ctx.columnName) {
          "int_field" -> value as Int + 2
          else -> value as Serializable
        }
      }
    }

    withTransformer(incrementIntTransformer)
    {
      transacter.transaction { session ->
        session.save(DbManyTypes(1, 1.2, "Test", "Bytes".toByteArray()))
      }
      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesRawQuery>()
                .intField(3)
                .list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(3)
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesQuery>()
                .intField(1)
                .list(session)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(1)
      }
    }
  }

  @Test
  fun testTransformerArguments() {
    val incrementByArgumentAmount = object : SwappableTransformer {
      override fun assemble(ctx: TransformerContext, owner: Any?, value: Serializable): Any {
        return when (ctx.columnName) {
          "int_field" -> value as Int - ctx.arguments["amount"] as Int
          else -> value
        }
      }

      override fun disassemble(ctx: TransformerContext, value: Any): Serializable {
        return when (ctx.columnName) {
          "int_field" -> value as Int + ctx.arguments["amount"] as Int
          else -> value as Serializable
        }
      }
    }

    withTransformer(incrementByArgumentAmount)
    {
      val value = 1

      transacter.transaction { session ->
        session.save(DbManyTypes(value, 1.2, "Test", "Bytes".toByteArray()))
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesQuery>()
                .intField(value)
                .list(session)
        assertThat(rows).hasSize(1)
        assertThat(rows.first().intField).isEqualTo(value)
      }

      transacter.transaction { session ->
        val rows = queryFactory.newQuery<ManyTypesRawQuery>()
                .intField(3)
                .list(session)

        val annotationForInt = DbManyTypes::class.declaredMemberProperties
                .mapNotNull {prop -> prop.javaField?.getAnnotation(TransformedInt::class.java) }
                .first()
        assertThat(rows).hasSize(1)
        assertThat(rows[0].intField).isEqualTo(value + annotationForInt.amount)
      }
    }
  }

  interface ManyTypesQuery : Query<DbManyTypes> {
    @Constraint(path = "intField")
    fun intField(intField: Int): ManyTypesQuery
  }

  interface ManyTypesRawQuery : Query<DbManyTypesRaw> {
    @Constraint(path = "intField")
    fun intField(intField: Int): ManyTypesRawQuery
  }

  interface ManyTypesProjectionQuery : Query<DbManyTypes> {
    @Constraint(path = "intField")
    fun intField(intField: Int): ManyTypesProjectionQuery

    @Select
    fun query(session: Session): List<ManyTypesProjection>
  }

  @Target(AnnotationTarget.FIELD)
  @TransformedType(transformer = DelegatingTransformer::class, targetType = Int::class)
  annotation class TransformedInt(val amount: Int = 0, val intVal: Int = 34)

  @Target(AnnotationTarget.FIELD)
  @TransformedType(transformer = DelegatingTransformer::class, targetType = Double::class)
  annotation class TransformedDouble


  @Target(AnnotationTarget.FIELD)
  @TransformedType(transformer = DelegatingTransformer::class, targetType = String::class)
  annotation class TransformedString

  @Target(AnnotationTarget.FIELD)
  @TransformedType(transformer = DelegatingTransformer::class, targetType = ByteArray::class)
  annotation class TransformedByteArray

  @Entity
  @Table(name = "manytypes")
  class DbManyTypes : DbUnsharded<DbManyTypes> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbManyTypes>

    @Column(name = "int_field")
    @TransformedInt(amount=2)
    var intField: Int = 0

    @Column(name = "double_field")
    @TransformedDouble
    var doubleField: Double = 0.0

    @Column(name = "string_field")
    @TransformedString
    var stringField: String = ""

    @Column(name = "byte_array_field", nullable=false)
    @TransformedByteArray
    var byteArrayField: ByteArray = byteArrayOf()

    constructor(intField: Int, doubleField: Double, stringField: String, byteArrayField: ByteArray) {
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

    constructor(intField: Int, doubleField: Double, stringField: String, byteArrayField: ByteArray) {
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
              Arrays.equals(byteArrayField, other.byteArrayField)
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
      install(EnvironmentModule(Environment.TESTING))

      bind<SwappableTransformer>().toInstance(object : SwappableTransformer {
        override fun assemble(ctx: TransformerContext, owner: Any?, value: Serializable): Any = swappableTransformer?.assemble(ctx, owner, value)!!
        override fun disassemble(ctx: TransformerContext, value: Any): Serializable = swappableTransformer?.disassemble(ctx, value)!!
      })

      val conf = MiskConfig.load<TransformedColumnTestConfig>("transformedcolumn", Environment.TESTING)
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