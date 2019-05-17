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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.annotations.Columns
import org.hibernate.exception.ConstraintViolationException
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
    val token = transacter.transaction { session ->
      queryFactory.newQuery(AuthQuery::class)
        .clientId(clientId)
        .list(session)[0]
    }
    assertThat(token.token).isEqualTo(clientToken)
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
      session.hibernateSession.createNativeQuery("update auth_tokens " +
          "set token=? where id=?")
          .setParameter(1, evilToken)
          .setParameter(2, id)
          .executeUpdate()
      session.hibernateSession.flush()
    }
    transacter.transaction { session ->
      assertThatThrownBy {
        queryFactory.newQuery(AuthQuery::class)
            .clientId(clientId)
            .list(session)[0]
      }.isInstanceOf(PersistenceException::class.java)
    }
  }

  /**
   * Test that given the salt is unique, one cannot copy paste a verified column to a new row.
   */
  @Test
  fun testCopyPastingTokenFails() {
    val clientId = "2345"
    val clientToken = UUID.randomUUID().toString()
    transacter.transaction { session ->
      val row = DbAuthToken(clientId, clientToken)
      session.save(row)
    }
    val row = transacter.transaction { session ->
      queryFactory.newQuery(AuthQuery::class)
          .clientId(clientId)
          .list(session)[0]
    }
    assertThatThrownBy {
      transacter.transaction { session ->
        session.hibernateSession.createNativeQuery("insert into auth_tokens " +
            "(client_id, token, token_salt, token_hmac) values (?, ?, ?, ?)")
            .setParameter(1, row.clientId)
            .setParameter(2, row.token)
            .setParameter(3, row.salt)
            .setParameter(4, row.hmac)
            .executeUpdate()
        session.hibernateSession.flush()
      }
    }.isInstanceOf(ConstraintViolationException::class.java)
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
      Column(name = "token_salt", nullable = true),
      Column(name = "token_hmac", nullable = true)
    ])
    var token: String?

    /**
     * This column is here only for testing purposes,
     * there's no need for it to be accessible in real situations.
     */
    @Column(name = "token_salt", nullable = true, insertable = false, updatable = false)
    var salt: ByteArray?

    /**
     * This column is here only for testing purposes,
     * there's no need for it to be accessible in real situations.
     */
    @Column(name = "token_hmac", nullable = true, insertable = false, updatable = false)
    var hmac: ByteArray?

    constructor(clientId: String, authToken: String, salt: ByteArray? = null, hmac: ByteArray? = null) {
      this.clientId = clientId
      this.token = authToken
      this.salt = salt
      this.hmac = hmac
    }
  }

  interface AuthQuery : Query<DbAuthToken> {
    @Constraint(path = "clientId")
    fun clientId(clientId: String): AuthQuery
  }

  data class AppConfig(val data_source: DataSourceConfig, val crypto: CryptoConfig) : Config

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<AppConfig>("verifiedcolumn", Environment.TESTING)
      install(CryptoTestModule(config.crypto.keys!!))
      install(HibernateTestingModule(AuthDb::class, config.data_source))
      install(HibernateModule(AuthDb::class, config.data_source))
      install(object : HibernateEntityModule(AuthDb::class) {
        override fun configureHibernate() {
          addEntities(DbAuthToken::class)
        }
      })
    }
  }
}