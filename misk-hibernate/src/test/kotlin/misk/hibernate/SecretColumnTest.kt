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
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.annotations.Columns
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.Arrays
import java.util.Objects
import javax.persistence.PersistenceException
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
    val reviewer = "Myself".toByteArray()
    transacter.transaction { session ->
      val song = DbJerryGarciaSong(title, length, album, reviewer)
      session.save(song)
    }
    // make sure the data in the database is not the same as the plaintext data
    transacter.transaction { session ->
      val persistedAlbum = session.hibernateSession.createNativeQuery(
          "select album from jerry_garcia_songs where title=\"$title\"").list()[0]
      assertThat(persistedAlbum).isNotEqualTo(album)
    }
    // test that when retrieving from the database we get the plaintext value
    transacter.transaction { session ->
      val song = queryFactory.newQuery(JerryGarciaSongQuery::class)
          .title("Dark Star")
          .query(session)[0]
      assertThat(song.title).isEqualTo(title)
      assertThat(song.length).isEqualTo(length)
      assertThat(song.album).isEqualTo(album)
      assertThat(song.reviewer).isEqualTo(reviewer)
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
      assertNull(song.reviewer)
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
	  ...
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
    val reviewer = "Myself".toByteArray()
    transacter.transaction { session ->
      // album here can be anything, just not a validly-encrypted album
      session.hibernateSession.createNativeQuery("INSERT into jerry_garcia_songs " +
          "(title, length, album, reviewer) values (?, ?, ?, ?)")
          .setParameter(1, title)
          .setParameter(2, length)
          .setParameter(3, album)
          .setParameter(4, reviewer)
          .executeUpdate()
      session.hibernateSession.flush()
    }
    assertThatThrownBy {
      transacter.transaction { session ->
        queryFactory.newQuery<JerryGarciaSongQuery>()
            .title(title)
            .query(session)[0]
      }
    }.isInstanceOf(PersistenceException::class.java)
  }

  @Test
  fun testGetRecordByEncryptedIndexedColumnSucceeds() {
    val title = "Dark Star"
    val length = 2918
    val album = "Live/Dead".toByteArray()
    val reviewer = "Reviewer".toByteArray()
    transacter.transaction { session ->
      session.save(DbJerryGarciaSong(title, length, album, reviewer))
      val songs = queryFactory.newQuery<JerryGarciaSongQuery>()
              .reviewer(reviewer)
              .query(session)
      assertThat(songs.size).isEqualTo(1)
    }
  }

  @Test
  fun testGetRecordByEncryptedNonIndexedColumnFails() {
    val title = "Dark Star"
    val length = 2918
    val album = "Live/Dead".toByteArray()
    transacter.transaction { session ->
      session.save(DbJerryGarciaSong(title, length, album))
      val songs = queryFactory.newQuery<JerryGarciaSongQuery>()
          .album(album)
          .query(session)
      assertThat(songs.size).isEqualTo(0)
    }
  }

  @Test
  fun testEncryptedIndxedQueryMultiple() {
    val reviewer = "Some Reviewer".toByteArray()
    transacter.transaction { session ->
      session.save(DbJerryGarciaSong("Sugaree", 123, "Steal Your Face".toByteArray(), reviewer))
      session.save(DbJerryGarciaSong("Truckin'", 124, "American Beauty".toByteArray(), reviewer))
      session.save(
          DbJerryGarciaSong("Eyes of the World", 125, "Wake of the Flood".toByteArray(), reviewer))
    }
    transacter.transaction { session ->
      val numOfIdenticalCiphertexts = session.hibernateSession.createNativeQuery(
          "select count(reviewer) from jerry_garcia_songs group by reviewer").list()
      assertThat(numOfIdenticalCiphertexts.size).isEqualTo(1)
      assertThat(numOfIdenticalCiphertexts[0]).isEqualTo(BigInteger.valueOf(3L))
    }
  }

  @Test
  fun testAuthenticatedEncryptedDataCannotBeCopiedOver() {
    val title = "Dark Star"
    val length = 2918
    val album = "Live/Dead".toByteArray()
    val reviewer = "Reviewer".toByteArray()
    transacter.transaction { session ->
      val row = DbJerryGarciaSong(title, length, album, reviewer)
      session.save(row)
    }
    transacter.transaction { session ->
      // get data from the existing row
      val encryptedAlbum = session.hibernateSession
          .createNativeQuery("select album from jerry_garcia_songs where title = ?")
          .setParameter(1, title)
          .list()[0] as ByteArray
      // try to copy the secret data over
      session.hibernateSession.createNativeQuery("insert into jerry_garcia_songs" +
          "(title, length, album) value (?, ?, ?)")
          .setParameter(1, "Sugar Magnolia") // was not in that album
          .setParameter(2, length)
          .setParameter(3, encryptedAlbum)
          .executeUpdate()
    }
    // make sure an exception is thrown because the data is not authenticated
    assertThatThrownBy {
      transacter.transaction { session ->
        queryFactory.newQuery<JerryGarciaSongQuery>()
            .title("Sugar Magnolia")
            .query(session)
      }
    }.isInstanceOf(PersistenceException::class.java)
  }

  @Test
  fun thatSecretColumnsCannotBeSwapped() {
    val title = "St. Stephen"
    val length = 616
    val album = "Live/Dead".toByteArray()
    val reviewer = "Charlie Miller".toByteArray()
    // put a record in the database
    val id = transacter.transaction { session ->
      val row = DbJerryGarciaSong(title, length, album, reviewer)
      session.save(row)
    }
    // swap between the album and reviewer columns
    transacter.transaction { session ->
      val (encryptedAlbum, encryptedReviewer) = session.hibernateSession
          .createNativeQuery("select album, reviewer from jerry_garcia_songs")
          .list()[0] as Array<*>
      session.hibernateSession.createNativeQuery("UPDATE jerry_garcia_songs set " +
          "album = ?," +
          "reviewer = ? " +
          "where id = ?")
          .setParameter(1, encryptedReviewer)
          .setParameter(2, encryptedAlbum)
          .setParameter(3, id)
          .executeUpdate()
    }
    // make sure an exception is thrown when trying to fetch the modified record
    assertThatThrownBy {
      transacter.transaction { session ->
        queryFactory.newQuery<JerryGarciaSongQuery>()
            .title(title)
            .query(session)
      }
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

    @SecretColumn(keyName="albumKey")
    @Columns(columns = [
      Column(name = "album", nullable = true),
      Column(name = "album_aad", nullable = true)
    ])
    var album: ByteArray?

    @SelectableSecretColumn(keyName="reviewerKey")
    @Column(name = "reviewer", nullable = true)
    var reviewer: ByteArray?

    constructor(title: String, length: Int, album: ByteArray? = null, reviewer: ByteArray? = null) {
      this.title = title
      this.length = length
      this.album = album
      this.reviewer = reviewer
    }
  }

  interface JerryGarciaSongQuery : Query<DbJerryGarciaSong> {
    @Constraint(path = "title")
    fun title(title: String): JerryGarciaSongQuery

    @Constraint(path = "album")
    fun album(album: ByteArray): JerryGarciaSongQuery

    @Constraint(path = "reviewer")
    fun reviewer(reviewer: ByteArray): JerryGarciaSongQuery

    @Select
    fun query(session: Session): List<SongInfo>
  }

  data class SongInfo(
    @Property("title") val title: String,
    @Property("length") val length: Int,
    @Property("album") val album: ByteArray?,
    @Property("reviewer") val reviewer: ByteArray?
  ) : Projection {
    override fun hashCode(): Int = Objects.hash(title, length, album, reviewer)
    override fun equals(other: Any?): Boolean {
      if (other == null) {
        return false
      }
      if (other !is SongInfo) {
        return false
      }
      return title == other.title && length == other.length && Arrays.equals(album, other.album) && Arrays.equals(reviewer, other.reviewer)
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
        }
      })
    }
  }
}