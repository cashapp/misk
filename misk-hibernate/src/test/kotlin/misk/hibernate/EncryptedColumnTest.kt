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
import okio.ByteString
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import okio.ByteString.Companion.toByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

@MiskTest(startService = true)
class EncryptedColumnTest {

  @MiskTestModule
  val module = TestModule()

  @Inject @JerryGarciaDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun testHappyPath() {
    val title = "Dark Star"
    val length = 2918 // longest recorded dark star was 47 minutes and 18 seconds
    val album = "Live/Dead".toByteArray().toByteString()
    transacter.transaction { session ->
      session.save(DbJerryGarciaSong(title, length, album))
    }
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
    @EncryptedColumn(keyName = "albumKey")
    var album: ByteString?

    constructor(title: String, length: Int, album: ByteString? = null) {
      this.title = title
      this.length = length
      this.album = album
    }
  }

  interface JerryGarciaSongQuery : Query<DbJerryGarciaSong> {
    @Constraint(path = "title")
    fun title(title: String): JerryGarciaSongQuery

    @Select
    fun query(session: Session): List<SongInfo>
  }

  data class SongInfo(
    @Property("title") val title: String,
    @Property("length") val length: Int,
    @Property("album") val album: ByteString?
  ) : Projection

  data class AppConfig(
    val data_source: DataSourceConfig,
    val crypto: CryptoConfig
  ) : Config

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<AppConfig>("encryptedcolumn", Environment.TESTING)
      install(CryptoTestModule(config.crypto))
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