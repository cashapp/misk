package misk.hibernate

import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.crypto.CryptoConfig
import misk.crypto.CryptoTestModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.annotations.Columns
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.PersistenceException
import javax.persistence.Table

@MiskTest(startService = true)
class VerifiedColumnTest {

  @MiskTestModule
  val module = TestModule()

  @Inject @AuthDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  /**
   * Test that we're able to fetch data from a table with a verified column
   */
  @Test
  fun testHappyPath() {
    val clientId = "123556"
    val clientToken = UUID.randomUUID().toString()
    transacter.transaction {session ->
      val row = DbAuthToken(clientId, clientToken)
      session.save(row)
    }
    transacter.transaction { session ->
      assertThatCode {
        queryFactory.newQuery(AuthQuery::class)
          .clientId(clientId)
          .query(session)[0]
      }.doesNotThrowAnyException()
    }
  }

  /**
   * Test that if the data has been modified without updating the MAC for it, an exception is thrown
   */
  @Test
  fun testHashDoesntMatch() {
    val clientId = "12345"
    val clientToken = UUID.randomUUID().toString()
    val id = transacter.transaction { session ->
      val row = DbAuthToken(clientId, clientToken)
      session.save(row)
    }
    transacter.transaction { session ->
      val evilToken = UUID.randomUUID().toString()
      val evilRowUpdate = DbAuthTokensRaw(id, clientId, evilToken)
      session.hibernateSession.update(evilRowUpdate)
    }
    transacter.transaction { session ->
      assertThatThrownBy {
        queryFactory.newQuery(AuthQuery::class)
            .clientId(clientId)
            .query(session)[0]
      }.isInstanceOf(PersistenceException::class.java)
    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class AuthDb

  @Entity
  @Table(name = "auth_tokens")
  class DbAuthToken : DbUnsharded<DbAuthToken> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbAuthToken>

    @Column(name = "client_id", nullable = false)
    var clientId: String

    @VerifiedColumn("tokenVerificationKey")
    @Columns(columns = [
      Column(name = "token", nullable = true),
      Column(name = "token_hash", nullable = true)
    ])
    var token: String?

    constructor(clientId: String, authToken: String) {
      this.clientId = clientId
      this.token = authToken
    }
  }

  @Entity
  @Table(name = "auth_tokens")
  class DbAuthTokensRaw : DbUnsharded<DbAuthTokensRaw> {

    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbAuthTokensRaw>

    @Column(name = "client_id", nullable = false)
    var clientId: String

    @Column(name = "token", nullable = true)
    var token: String?

    constructor(id: Id<DbAuthToken>? = null, clientId: String, token: String) {
      if (id != null) {
        this.id = Id(id.id)
      }
      this.clientId = clientId
      this.token = token
    }
  }

  interface AuthQuery : Query<DbAuthToken> {
    @Constraint(path = "clientId")
    fun clientId(clientId: String): AuthQuery

    @Select
    fun query(session: Session): List<AuthToken>
  }

  data class AuthToken(
    @Property("clientId") val clientId: String,
    @Property("token") val token: String?

  ) : Projection {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as AuthToken

      if (clientId != other.clientId) return false
      if (token != null) {
        if (other.token == null) return false
        if (!token.contentEquals(other.token)) return false
      } else if (other.token != null) return false

      return true
    }

    override fun hashCode(): Int {
      var result = clientId.hashCode()
      result = 31 * result + (token?.hashCode() ?: 0)
      return result
    }
  }

  data class AppConfig(val data_source: DataSourceConfig, val crypto: CryptoConfig) : Config

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<AppConfig>("authenticationcolumn", Environment.TESTING)
      install(CryptoTestModule(config.crypto.keys!!))
      install(HibernateTestingModule(AuthDb::class, config.data_source))
      install(HibernateModule(AuthDb::class, config.data_source))
      install(object : HibernateEntityModule(AuthDb::class) {
        override fun configureHibernate() {
          addEntities(DbAuthToken::class, DbAuthTokensRaw::class)
        }
      })
    }
  }
}