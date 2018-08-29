package misk.hibernate

import misk.MiskServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

@MiskTest(startService = true)
class ByteStringColumnTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @ByteStringColumn lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun happyPath() {
    val abcHash = "abc".encodeUtf8().sha256()
    val defHash = "def".encodeUtf8().sha256()
    transacter.transaction { session ->
      session.save(DbTextHash("abc", abcHash))
      session.save(DbTextHash("def", defHash))
    }
    transacter.transaction { session ->
      val textHash = queryFactory.newQuery(TextHashQuery::class)
          .hash(abcHash)
          .uniqueResult(session)!!
      assertThat(textHash.text).isEqualTo("abc")
      assertThat(textHash.hash).isEqualTo(abcHash)
    }
  }

  @Test
  fun sorting() {
    val v1 = TextAndHash("00", "00".decodeHex())
    val v2 = TextAndHash("99", "99".decodeHex())
    val v3 = TextAndHash("a0", "a0".decodeHex())
    val v4 = TextAndHash("ff", "ff".decodeHex())

    transacter.transaction { session ->
      session.save(DbTextHash(v1.text, v1.hash))
      session.save(DbTextHash(v2.text, v2.hash))
      session.save(DbTextHash(v3.text, v3.hash))
      session.save(DbTextHash(v4.text, v4.hash))
    }

    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<TextHashQuery>()
          .hashLessThan(v1.hash)
          .listAsTextAndHash(session))
          .isEmpty()
      assertThat(queryFactory.newQuery<TextHashQuery>()
          .hashLessThan(v2.hash)
          .listAsTextAndHash(session))
          .containsExactly(v1)
      assertThat(queryFactory.newQuery<TextHashQuery>()
          .hashLessThan(v3.hash)
          .listAsTextAndHash(session))
          .containsExactly(v1, v2)
      assertThat(queryFactory.newQuery<TextHashQuery>()
          .hashLessThan(v4.hash)
          .listAsTextAndHash(session))
          .containsExactly(v1, v2, v3)
      assertThat(queryFactory.newQuery<TextHashQuery>()
          .hashLessThan("ff00".decodeHex())
          .listAsTextAndHash(session))
          .containsExactly(v1, v2, v3, v4)
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<RootConfig>("bytestringcolumn", Environment.TESTING)
      install(HibernateTestingModule(ByteStringColumn::class))
      install(HibernateModule(ByteStringColumn::class, config.data_source))
      install(object : HibernateEntityModule(ByteStringColumn::class) {
        override fun configureHibernate() {
          addEntities(DbTextHash::class)
        }
      })
    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class ByteStringColumn

  data class RootConfig(val data_source: DataSourceConfig) : Config

  @Entity
  @Table(name = "text_hashes")
  class DbTextHash() : DbEntity<DbTextHash> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbTextHash>

    @Column(nullable = false)
    lateinit var text: String

    @Column(nullable = false)
    lateinit var hash: ByteString

    constructor(text: String, hash: ByteString) : this() {
      this.text = text
      this.hash = hash
    }
  }

  data class TextAndHash(
    @Property("text") val text: String,
    @Property("hash") val hash: ByteString
  ) : Projection

  interface TextHashQuery : Query<DbTextHash> {
    @Constraint(path = "hash")
    fun hash(hash: ByteString): TextHashQuery

    @Constraint(path = "hash", operator = Operator.LT)
    fun hashLessThan(hash: ByteString): TextHashQuery

    @Select
    fun listAsTextAndHash(session: Session): List<TextAndHash>
  }
}
