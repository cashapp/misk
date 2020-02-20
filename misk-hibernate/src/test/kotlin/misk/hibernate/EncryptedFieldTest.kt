package misk.hibernate

import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.crypto.CryptoConfig
import misk.crypto.CryptoTestModule
import misk.crypto.InvalidEncryptionContextException
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.hibernate.encryption.EncryptedField
import misk.hibernate.encryption.EncryptedFieldContext
import misk.hibernate.encryption.EncryptedFieldType
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.UndeclaredThrowableException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.Table

@MiskTest(startService = true)
class EncryptedFieldTest {

  @MiskTestModule
  val module = TestModule()

  @Inject
  @FieldLevelEncryptionDb
  lateinit var transacter: Transacter
  @Inject
  lateinit var queryFactory: Query.Factory

  /**
   * Test that the value is being encrypted when persisted in the database and decrypted when fetched.
   */
  @Test
  fun testRoundTripIndexed() {
    val name = "Donald Knuth"
    val aliases = 4
    val address = "123 Main St"
    val image = byteArrayOf(1, 2, 3)
    val ssn = "000-44-1234".toByteArray()

    transacter.transaction { session ->
      val song = DbTestCustomer(name, aliases, address, image, ssn)
      session.save(song)
    }

    transacter.transaction { session ->
      val customerRaw = queryFactory.newQuery(TestCustomerRawQuery::class)
          .name(name)
          .list(session).first()

      assertThat(customerRaw.address).isNotEqualTo(address)
      assertThat(customerRaw.ssn).isNotEqualTo(ssn)
      assertThat(customerRaw.image).isNotEqualTo(image)
    }

    transacter.transaction { session ->
      val customer = queryFactory.newQuery(TestCustomerQuery::class)
          .ssn(ssn)
          .list(session).first()

      assertThat(customer.name).isEqualTo(name)
      assertThat(customer.aliases).isEqualTo(aliases)
      assertThat(customer.address).isEqualTo(address)
      assertThat(customer.image).isEqualTo(image)
      assertThat(customer.ssn).isEqualTo(ssn)
    }
  }

  @Test
  fun testRoundTripNotIndexed() {
    val name = "Donald Knuth"
    val aliases = 4
    val address = "123 Main St"
    val image = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
    val ssn = "000-44-1234".toByteArray()

    transacter.transaction { session ->
      val song = DbTestCustomer(name, aliases, address, image, ssn)
      session.save(song)
    }

    // test that when retrieving from the database we get the plaintext value
    transacter.transaction { session ->
      val customer = queryFactory.newQuery(TestCustomerQuery::class)
          .list(session).first()

      assertThat(customer.name).isEqualTo(name)
      assertThat(customer.aliases).isEqualTo(aliases)
      assertThat(customer.address).isEqualTo(address)
      assertThat(customer.image).isEqualTo(image)
      assertThat(customer.ssn).isEqualTo(ssn)
    }
  }

  @Test
  fun testUpdateIndexed() {
    val name = "Grace Hopper"
    val aliases = 4
    val address = "123 Main St"
    val image = byteArrayOf(1, 2, 3, 4)
    val ssn = "000-44-1234".toByteArray()

    transacter.transaction { session ->
      val song = DbTestCustomer(name, aliases, address, image, ssn)
      session.save(song)
    }

    val newSsn = "000-55-1234".toByteArray()
    // test that when retrieving from the database we get the plaintext value
    transacter.transaction { session ->
      val customer = queryFactory.newQuery(TestCustomerQuery::class)
          .list(session).first()

      customer.ssn = newSsn
      session.save(customer)
    }

    transacter.transaction { session ->
      val customers = queryFactory.newQuery(TestCustomerQuery::class)
          .ssn(newSsn)
          .list(session)

      assertThat(customers).hasSize(1)
      assertThat(customers.first().ssn).isEqualTo(newSsn)
    }
  }

  @Test
  fun testUpdateNonIndexed() {
    val name = "Donald Knuth"
    val aliases = 4
    val address = "123 Main St"
    val image = byteArrayOf(5, 4, 3, 2, 1)
    val ssn = "000-44-1234".toByteArray()

    transacter.transaction { session ->
      val song = DbTestCustomer(name, aliases, address, image, ssn)
      session.save(song)
    }

    val newAddress = "another address"
    // test that when retrieving from the database we get the plaintext value
    transacter.transaction { session ->
      val customer = queryFactory.newQuery(TestCustomerQuery::class)
          .list(session).first()

      customer.address = newAddress
      session.save(customer)
    }

    transacter.transaction { session ->
      val customer = queryFactory.newQuery(TestCustomerQuery::class)
          .list(session).first()

      assertThat(customer.address).isEqualTo(newAddress)
    }
  }

  @Test
  fun testNullValue() {
    val name = "John Doe"
    val aliases = 1
    transacter.transaction { session ->
      session.save(DbTestCustomer(name, aliases))
    }
    transacter.transaction { session ->
      val song = queryFactory.newQuery(TestCustomerQuery::class)
          .name(name)
          .list(session)[0]

      assertThat(song.name).isEqualTo(name)
      assertThat(song.aliases).isEqualTo(aliases)
      assertThat(song.address).isNull()
      assertThat(song.ssn).isNull()
    }
  }

  /**
   * Test that in case we fail to decrypt a value, we raise an exception
   */
  @Test
  fun testFailedDecryption() {
    val name = "First Last"
    val address = "Here"
    val ssn = "000-00-0000".toByteArray()
    transacter.transaction { session ->
      // address here can be anything, just not a validly-encrypted address
      session.save(DbTestCustomerRaw(name, 0, address, byteArrayOf(), ssn))

      // This should actually throw InvalidEncryptionContextException, but due to the reflection query, it percolates
      // up as UndeclaredThrowableException
      assertThrows<UndeclaredThrowableException> {
        queryFactory.newQuery<TestCustomerQuery>()
            .name(name)
            .list(session)[0]
      }
    }
  }

  @Test
  fun testGetCustomerByEncryptedIndexedColumnSucceeds() {
    val name = "First Last"
    val address = "Here"
    val ssn = "000-00-0000".toByteArray()

    transacter.transaction { session ->
      // address here can be anything, just not a validly-encrypted address
      session.save(DbTestCustomer(name, 0, address, byteArrayOf(), ssn))

      val songs = queryFactory.newQuery<TestCustomerQuery>()
          .ssn(ssn)
          .list(session)

      assertThat(songs.size).isEqualTo(1)
    }
  }

  @Test
  fun testBasicUpdate() {
    val name = "Donald Knuth"
    val aliases = 4
    val address = "123 Main St"
    val image = byteArrayOf(1, 2, 3)
    val newImage = "some image representation".toByteArray()
    val ssn = "000-44-1234".toByteArray()

    val newCustomer = DbTestCustomer(name, aliases, address, image, ssn)

    transacter.transaction { session ->
      session.save(newCustomer)
    }

    transacter.transaction { session ->
      val customerRaw = queryFactory.newQuery(TestCustomerRawQuery::class)
          .name(name)
          .list(session).first()

      assertThat(customerRaw.address).isNotEqualTo(address)
      assertThat(customerRaw.ssn).isNotEqualTo(ssn)
      assertThat(customerRaw.image).isNotEqualTo(image)
    }

    transacter.transaction { session ->
      newCustomer.image = newImage
      session.hibernateSession.saveOrUpdate(newCustomer)
    }

    transacter.transaction { session ->
      val customer = queryFactory.newQuery(TestCustomerQuery::class)
          .ssn(ssn)
          .list(session).first()

      assertThat(customer.name).isEqualTo(name)
      assertThat(customer.aliases).isEqualTo(aliases)
      assertThat(customer.address).isEqualTo(address)
      assertThat(customer.image).isEqualTo(newImage)
      assertThat(customer.ssn).isEqualTo(ssn)
    }
  }

  @Test
  fun testCorruptPacket() {
    val name = "Grace Hopper"
    val aliases = 4
    val address = "123 Main St"
    val image = byteArrayOf(1, 2, 3)
    val ssn = "000-44-1234".toByteArray()

    val newCustomer = DbTestCustomer(name, aliases, address, image, ssn)

    transacter.transaction { session ->
      session.save(newCustomer)
    }

    transacter.transaction { session ->
      val customerRaw = queryFactory.newQuery(TestCustomerRawQuery::class)
          .name(name)
          .list(session).first()

      customerRaw.ssn?.set(0, 12)
      session.hibernateSession.saveOrUpdate(customerRaw)
    }

    // This should actually throw InvalidEncryptionContextException, but due to the reflection query, it percolates
    // up as UndeclaredThrowableException
    val throwable = assertThrows<UndeclaredThrowableException> {
      transacter.transaction { session ->
        queryFactory.newQuery(TestCustomerQuery::class)
            .list(session).first()
      }
    }
    assertThat(throwable.undeclaredThrowable).isInstanceOf(InvalidEncryptionContextException::class.java)
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class FieldLevelEncryptionDb

  @Entity
  @Table(name = "customers")
  class DbTestCustomer constructor() : DbUnsharded<DbTestCustomer> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbTestCustomer>

    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = false)
    var aliases: Int = 0

    @Column(nullable = true)
    @EncryptedField("addressKey", type = EncryptedFieldType.NonDeterministic)
    var address: String? = null

    @Column(nullable = true)
    @EncryptedField("ssnKey")
    var image: ByteArray? = null

    @Column(name = "ssn_column", nullable = true)
    @EncryptedField("ssnKey", type = EncryptedFieldType.Indexable)
    var ssn: ByteArray? = null

    @ManyToOne
    @JoinColumn(name = "group_id")
    var group: DbTestCustomerGroup? = null

    @EncryptedFieldContext("address")
    fun addressContext(): Map<String, String> {
      val context = mutableMapOf(
          "constantKey" to "constantValue",
          "aliases" to "$aliases"
      )
      if (::id.isInitialized) {
        context["id"] = id.toString()
      }
      return context
    }

    constructor(name: String, aliases: Int, address: String? = null, image: ByteArray? = null, ssn: ByteArray? = null) : this() {
      this.name = name
      this.aliases = aliases
      this.address = address
      this.image = image
      this.ssn = ssn
    }
  }

  @Entity
  @Table(name = "customer_group")
  class DbTestCustomerGroup constructor() : DbUnsharded<DbTestCustomerGroup> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbTestCustomerGroup>

    @Column
    lateinit var name: String

    @OneToMany
    @JoinColumn(name = "group_id")
    lateinit var customers: List<DbTestCustomer>

    constructor(name: String, customers: List<DbTestCustomer>) : this() {
      this.name = name
      this.customers = customers
    }
  }

  @Entity
  @Table(name = "customers")
  class DbTestCustomerRaw constructor() : DbUnsharded<DbTestCustomerRaw> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbTestCustomerRaw>

    @Column(nullable = false)
    var name: String? = null

    @Column(nullable = false)
    var aliases: Int = 0

    @Column(nullable = true)
    var address: String? = null

    @Column(nullable = true)
    var image: ByteArray? = null

    @Column(name = "ssn_column", nullable = true)
    var ssn: ByteArray? = null

    @ManyToOne
    @JoinColumn(name = "group_id")
    var group: DbTestCustomerGroup? = null

    constructor(name: String, aliases: Int, address: String? = null, image: ByteArray? = null, ssn: ByteArray? = null) : this() {
      this.name = name
      this.aliases = aliases
      this.address = address
      this.image = image
      this.ssn = ssn
    }
  }

  interface TestCustomerQuery : Query<DbTestCustomer> {

    @Constraint(path = "name")
    fun name(name: String): TestCustomerQuery

    @Constraint(path = "address")
    fun address(address: String): TestCustomerQuery

    @Constraint(path = "ssn")
    fun ssn(ssn: ByteArray): TestCustomerQuery

    @Constraint(path = "image")
    fun image(image: ByteArray): TestCustomerQuery
  }

  interface TestCustomerRawQuery : Query<DbTestCustomerRaw> {
    @Constraint(path = "name")
    fun name(name: String): TestCustomerRawQuery
  }

  data class AppConfig(
    val data_source: DataSourceConfig,
    val crypto: CryptoConfig
  ) : Config

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<AppConfig>("encryptedfield", Environment.TESTING)
      install(CryptoTestModule(config.crypto.keys!!))
      install(HibernateTestingModule(FieldLevelEncryptionDb::class, config.data_source))
      install(HibernateModule(FieldLevelEncryptionDb::class, config.data_source))
      install(object : HibernateEntityModule(FieldLevelEncryptionDb::class) {
        override fun configureHibernate() {
          addEntities(DbTestCustomer::class, DbTestCustomerGroup::class, DbTestCustomerRaw::class)
        }
      })
    }
  }
}