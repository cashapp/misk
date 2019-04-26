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
import org.assertj.core.api.Assertions
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.HibernateException
import org.junit.jupiter.api.Test
import java.util.Arrays
import java.util.Objects
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@MiskTest(startService = true)
class SecretColumnTest {

  @MiskTestModule
  val module = TestModule()

  @Inject @JerryGarciaDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  /**
   * Test that the value is being encrypted when persisted in the database and decrypted when fetched.
   */
  @Test
  fun testHappyPath() {
    val title = "Dark Star"
    val length = 2918 // longest recorded dark star was 47 minutes and 18 seconds
    val album = "Live/Dead".toByteArray()
    transacter.transaction { session ->
      session.save(DbJerryGarciaSong(title, length, album))
    }
    // make sure the data in the database is not the same as the plaintext data
    transacter.transaction { session ->
      val songRaw = queryFactory.newQuery(JerryGarciaSongRawQuery::class)
          .title("Dark Star")
          .query(session)[0]
      assertThat(songRaw.album).isNotEqualTo(album)
    }
    // test that when retrieving from the database we get the plaintext value
    transacter.transaction { session ->
      val song = queryFactory.newQuery(JerryGarciaSongQuery::class)
          .title("Dark Star")
          .query(session)[0]
      assertThat(song.title).isEqualTo(title)
      assertThat(song.length).isEqualTo(length)
      assertThat(song.album).isEqualTo(album)
    }
  }

  @Test
  fun testNullValue() {
    val title = "Ripple"
    val length = 165
    transacter.transaction { session ->
      session.save(DbJerryGarciaSong(title, length))
    }
    transacter.transaction { session ->
      val song = queryFactory.newQuery(JerryGarciaSongQuery::class)
          .title("Ripple")
          .query(session)[0]
      assertThat(song.title).isEqualTo(title)
      assertThat(song.length).isEqualTo(length)
      assertNull(song.album)
    }
  }

  /*
  This test is commented out because it will cause Hibernate to fail initialization
  Which is exactly what you want in case you specify a key that doesn't exist.
  This is the exception you'll get during startup:
  java.lang.IllegalStateException: Expected to be healthy after starting. The following services are not running: {STARTING=[TruncateTablesService [NEW], SchemaMigratorService [NEW], SchemaValidatorService [NEW]], FAILED=[SessionFactoryService [FAILED]]}

	at com.google.common.util.concurrent.ServiceManager$ServiceManagerState.checkHealthy(ServiceManager.java:741)
	at com.google.common.util.concurrent.ServiceManager$ServiceManagerState.awaitHealthy(ServiceManager.java:568)
	at com.google.common.util.concurrent.ServiceManager.awaitHealthy(ServiceManager.java:329)
	...
	Suppressed: com.google.common.util.concurrent.ServiceManager$FailedService: SessionFactoryService [FAILED]
	Caused by: org.hibernate.MappingException: Unable to instantiate custom type: misk.hibernate.SecretColumnType
		at org.hibernate.type.TypeFactory.custom(TypeFactory.java:169)
		at org.hibernate.type.TypeFactory.byClass(TypeFactory.java:74)
		at org.hibernate.type.TypeResolver.heuristicType(TypeResolver.java:124)
		at org.hibernate.mapping.SimpleValue.getType(SimpleValue.java:471)
		at org.hibernate.mapping.SimpleValue.isValid(SimpleValue.java:453)
		at org.hibernate.mapping.Property.isValid(Property.java:226)
		at org.hibernate.mapping.PersistentClass.validate(PersistentClass.java:624)
		at org.hibernate.mapping.RootClass.validate(RootClass.java:267)
		at org.hibernate.boot.internal.MetadataImpl.validate(MetadataImpl.java:347)
		at org.hibernate.boot.internal.SessionFactoryBuilderImpl.build(SessionFactoryBuilderImpl.java:466)
		at org.hibernate.boot.internal.MetadataImpl.buildSessionFactory(MetadataImpl.java:188)
		at misk.hibernate.SessionFactoryService.startUp(SessionFactoryService.kt:135)
		at com.google.common.util.concurrent.AbstractIdleService$DelegateService$1.run(AbstractIdleService.java:62)
		at com.google.common.util.concurrent.Callables$4.run(Callables.java:119)
		at java.base/java.lang.Thread.run(Thread.java:834)
	Caused by: org.hibernate.HibernateException: Cannot set field, key wrongKey not found
   */
//  @Test
//  fun testNoSuchKey() {
//    val title = "Dark Star"
//    val length = 2918 // longest recorded dark star was 47 minutes and 18 seconds
//    val album = "Live/Dead".toByteArray()
//    transacter.transaction { session ->
//      session.save(DbJerryGarciaSongWrongKey(title, length, album))
//    }
//  }
//  @Entity
//  @Table(name = "jerry_garcia_songs")
//  class DbJerryGarciaSongWrongKey : DbUnsharded<DbJerryGarciaSongWrongKey> {
//    @javax.persistence.Id
//    @GeneratedValue
//    override lateinit var id: Id<DbJerryGarciaSongWrongKey>
//
//    @Column(nullable = false)
//    var title: String
//
//    @Column(nullable = false)
//    var length: Int = 0
//
//    @Column(nullable = true)
//    @SecretColumn(keyName = "wrongKey")
//    var album: ByteArray?
//
//    constructor(title: String, length: Int, album: ByteArray? = null) {
//      this.title = title
//      this.length = length
//      this.album = album
//    }
//  }

  /**
   * Test that in case we fail to decrypt a value, we raise an exception
   */
  @Test
  fun testFailedDecryption() {
    val title = "Dark Star"
    val length = 2918
    val album = "Live/Dead".toByteArray()
    transacter.transaction { session ->
      // album here can be anything, just not a validly-encrypted album
      session.save(DbJerryGarciaSongRaw(title, length, album))

      assertThatThrownBy {
        queryFactory.newQuery<JerryGarciaSongQuery>()
            .title(title)
            .query(session)[0]
      }.isInstanceOf(javax.persistence.PersistenceException::class.java)
    }
  }

  @Test
  fun testGetRecordByEncryptedColumn() {
    val title = "Dark Star"
    val length = 2918
    val album = "Live/Dead".toByteArray()
    transacter.transaction { session ->
      session.save(DbJerryGarciaSong(title, length, album))
      val song = queryFactory.newQuery<JerryGarciaSongQuery>()
          .album(album)
          .query(session)
      assertNotNull(song)
    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class JerryGarciaDb

  @Entity
  @Table(name = "jerry_garcia_songs")
  class DbJerryGarciaSong : DbUnsharded<DbJerryGarciaSong> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbJerryGarciaSong>

    @Column(nullable = false)
    var title: String

    @Column(nullable = false)
    var length: Int = 0

    @Column(nullable = true)
    @SecretColumn(keyName = "albumKey")
    var album: ByteArray?

    constructor(title: String, length: Int, album: ByteArray? = null) {
      this.title = title
      this.length = length
      this.album = album
    }
  }

  @Entity
  @Table(name = "jerry_garcia_songs")
  class DbJerryGarciaSongRaw : DbUnsharded<DbJerryGarciaSongRaw> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbJerryGarciaSongRaw>

    @Column(nullable = false)
    var title: String

    @Column(nullable = false)
    var length: Int = 0

    @Column(nullable = true)
    var album: ByteArray?

    constructor(title: String, length: Int, album: ByteArray? = null) {
      this.title = title
      this.length = length
      this.album = album
    }
  }

  interface JerryGarciaSongQuery : Query<DbJerryGarciaSong> {
    @Constraint(path = "title")
    fun title(title: String): JerryGarciaSongQuery

    @Constraint(path = "album")
    fun album(album: ByteArray): JerryGarciaSongQuery

    @Select
    fun query(session: Session): List<SongInfo>
  }

  interface JerryGarciaSongRawQuery : Query<DbJerryGarciaSongRaw> {
    @Constraint(path = "title")
    fun title(title: String): JerryGarciaSongRawQuery

    @Select
    fun query(session: Session): List<SongInfo>
  }

  data class SongInfo(
    @Property("title") val title: String,
    @Property("length") val length: Int,
    @Property("album") val album: ByteArray?
  ) : Projection {
    override fun hashCode(): Int = Objects.hash(title, length, album)
    override fun equals(other: Any?): Boolean {
      if (other == null) {
        return false
      }
      if (other !is SongInfo) {
        return false
      }
      return title == other.title && length == other.length && Arrays.equals(album, other.album)
    }
  }

  data class AppConfig(
    val data_source: DataSourceConfig,
    val crypto: CryptoConfig
  ) : Config

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<AppConfig>("encryptedcolumn", Environment.TESTING)
      install(CryptoTestModule(config.crypto.keys!!))
      install(HibernateTestingModule(JerryGarciaDb::class, config.data_source))
      install(HibernateModule(JerryGarciaDb::class, config.data_source))
      install(object : HibernateEntityModule(JerryGarciaDb::class) {
        override fun configureHibernate() {
          addEntities(DbJerryGarciaSong::class)
          addEntities(DbJerryGarciaSongRaw::class)
        }
      })
    }
  }
}