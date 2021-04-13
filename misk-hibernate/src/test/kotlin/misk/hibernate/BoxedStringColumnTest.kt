package misk.hibernate

import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.config.Config
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

@MiskTest(startService = true)
class BoxedStringColumnTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @TokenColumn lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun happyPath() {
    val abcToken = GoodLuckToken("abc")
    val abcOptionalToken = GoodLuckToken("abc optional")

    val defToken = GoodLuckToken("def")
    val defOptionalToken = GoodLuckToken("def optional")
    transacter.transaction { session ->
      session.save(DbTextToken("abc", abcToken, abcOptionalToken))
      session.save(DbTextToken("def", defToken, defOptionalToken))
    }
    transacter.transaction { session ->
      val textHash = queryFactory.newQuery(TextTokenQuery::class)
        .token(abcToken)
        .uniqueResult(session)!!
      assertThat(textHash.text).isEqualTo("abc")
      assertThat(textHash.token).isEqualTo(abcToken)
      assertThat(textHash.optional_token).isEqualTo(abcOptionalToken)
    }
  }

  @Test
  fun nullOptionalToken() {
    val abcToken = GoodLuckToken("abc")
    transacter.transaction { session ->
      session.save(DbTextToken("abc", abcToken))
    }
    transacter.transaction { session ->
      val textHash = queryFactory.newQuery(TextTokenQuery::class)
        .token(abcToken)
        .uniqueResult(session)!!
      assertThat(textHash.text).isEqualTo("abc")
      assertThat(textHash.token).isEqualTo(abcToken)
      assertThat(textHash.optional_token).isNull()
    }
  }

  @Test
  fun sorting() {
    val v1 = TextAndToken("Uppercase A", GoodLuckToken("A"))
    val v2 = TextAndToken("Uppercase Z", GoodLuckToken("Z"))
    val v3 = TextAndToken("Lowercase a", GoodLuckToken("a"))
    val v4 = TextAndToken("Lowercase z", GoodLuckToken("z"))

    transacter.transaction { session ->
      session.save(DbTextToken(v1.text, v1.token))
      session.save(DbTextToken(v2.text, v2.token))
      session.save(DbTextToken(v3.text, v3.token))
      session.save(DbTextToken(v4.text, v4.token))
    }

    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<TextTokenQuery>()
          .tokenLessThan(v1.token)
          .listAsTextAndToken(session)
      )
        .isEmpty()
      assertThat(
        queryFactory.newQuery<TextTokenQuery>()
          .tokenLessThan(v2.token)
          .listAsTextAndToken(session)
      )
        .containsExactly(v1)
      assertThat(
        queryFactory.newQuery<TextTokenQuery>()
          .tokenLessThan(v3.token)
          .listAsTextAndToken(session)
      )
        .containsExactly(v1, v2)
      assertThat(
        queryFactory.newQuery<TextTokenQuery>()
          .tokenLessThan(v4.token)
          .listAsTextAndToken(session)
      )
        .containsExactly(v1, v2, v3)
      assertThat(
        queryFactory.newQuery<TextTokenQuery>()
          .tokenLessThan(GoodLuckToken("~"))
          .listAsTextAndToken(session)
      )
        .containsExactly(v1, v2, v3, v4)
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<RootConfig>("boxedstring", Environment.TESTING)
      install(HibernateTestingModule(TokenColumn::class, config.data_source))
      install(HibernateModule(TokenColumn::class, config.data_source))
      install(object : HibernateEntityModule(TokenColumn::class) {
        override fun configureHibernate() {
          addEntities(DbTextToken::class)
        }
      })
    }
  }

  data class GoodLuckToken(val string: String) : Comparable<GoodLuckToken> {
    override fun compareTo(other: GoodLuckToken) = string.compareTo(other.string)
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class TokenColumn

  data class RootConfig(val data_source: DataSourceConfig) : Config

  @Entity
  @Table(name = "text_tokens")
  class DbTextToken() : DbUnsharded<DbTextToken> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbTextToken>

    @Column(nullable = false)
    lateinit var text: String

    @Column(nullable = false)
    lateinit var token: GoodLuckToken

    @Column
    var optional_token: GoodLuckToken? = null

    constructor(text: String, token: GoodLuckToken, optionalToken: GoodLuckToken? = null) : this() {
      this.text = text
      this.token = token
      this.optional_token = optionalToken
    }
  }

  data class TextAndToken(
    @Property("text") val text: String,
    @Property("token") val token: GoodLuckToken
  ) : Projection

  interface TextTokenQuery : Query<DbTextToken> {
    @Constraint(path = "token")
    fun token(token: GoodLuckToken): TextTokenQuery

    @Constraint(path = "token", operator = Operator.LT)
    fun tokenLessThan(token: GoodLuckToken): TextTokenQuery

    @Select
    fun listAsTextAndToken(session: Session): List<TextAndToken>
  }
}
