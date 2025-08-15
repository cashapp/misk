package misk.jooq.listeners

import jakarta.inject.Inject
import misk.jooq.JooqTransacter
import misk.jooq.config.ClientJooqTestingModule
import misk.jooq.config.JooqDBIdentifier
import misk.jooq.testgen.tables.references.RECORD_SIGNATURE_TEST
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.jdbc.DataSourceService
import misk.jooq.testgen.tables.RecordSignatureTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach

@MiskTest(startService = true)
class RecordSignatureListenerTest {
  @MiskTestModule
  private var module = ClientJooqTestingModule()

  @Inject
  @JooqDBIdentifier
  private lateinit var transacter: JooqTransacter

  @Inject
  @JooqDBIdentifier
  private lateinit var dataSourceService: DataSourceService

  @BeforeEach
  fun cleanDatabase() {
    transacter.transaction { session ->
      session.ctx.deleteFrom(
        RecordSignatureTest.RECORD_SIGNATURE_TEST,
      )
    }
  }

  @Test
  fun `signature column not null on insert`() {
    transacter.transaction { (ctx) ->
      ctx.newRecord(RECORD_SIGNATURE_TEST).apply {
        this.name = "test"
        this.createdBy = "foo"
        this.binaryData = "some-data".toByteArray()
      }.also { it.store() }
    }

    transacter.transaction { (ctx) ->
      ctx.selectFrom(RECORD_SIGNATURE_TEST)
        .fetchOne()!!
        .also {
          assertNotNull(it.recordSignature)
        }
    }
  }

  @Test
  fun `signature updated when record is updated`() {
    // 1. insert new record
    val originalRecord = transacter.transaction { (ctx) ->
      ctx.newRecord(RECORD_SIGNATURE_TEST).apply {
        this.name = "test"
        this.createdBy = "foo"
        this.binaryData = "some-data".toByteArray()
      }.also { it.store() }
    }

    val originalSignature = originalRecord.recordSignature

    // 2. update existing record's updated_by column by id
    // must be a record based update, not global update like ctx.update()
    transacter.transaction { (ctx) ->
      val record = ctx.selectFrom(RECORD_SIGNATURE_TEST)
        .where(RECORD_SIGNATURE_TEST.ID.eq(originalRecord.id))
        .fetchOne()!!
      record.updatedBy = "some guy"
      record.store()
    }

    // 3. select exiting record by id, signature should not be null, and different from original signature
    transacter.transaction { (ctx) ->
      ctx.selectFrom(RECORD_SIGNATURE_TEST)
        .where(RECORD_SIGNATURE_TEST.ID.eq(originalRecord.id))
        .fetchOne()!!
        .also {
          assertNotNull(it.recordSignature)
          assertNotEquals(originalSignature, it.recordSignature)
        }
    }
  }

  @Test
  fun `throws error when retrieving rows updated directly in database`() {
    // 1. insert new record
    val record = transacter.transaction { (ctx) ->
      ctx.newRecord(RECORD_SIGNATURE_TEST).apply {
        this.name = "test-123"
        this.createdBy = "foo"
        this.binaryData = "some-data".toByteArray()
      }.also { it.store() }
    }

    // 2. use raw JDBC connection to modify existing record directly
    dataSourceService.dataSource.connection.use { connection ->
      connection.autoCommit = false
      try {
        connection.prepareStatement(
          """
          UPDATE misk_jooq_testing_writer.record_signature_test SET name = ? WHERE id = ?
          """,
        ).use { stmt ->
          stmt.setString(1, "test-456")
          stmt.setLong(2, record.id!!)
          stmt.execute()
        }
        connection.commit()
      } catch (e: Exception) {
        connection.rollback()
        throw e
      } finally {
        connection.autoCommit = true
      }
    }

    // 3. should throw when trying to verify on read with the listener-enabled transacter
    assertThrows<DataIntegrityException> {
      transacter.transaction { (ctx) ->
        // Let's also verify that the database was actually updated
        val result = ctx.selectFrom(RECORD_SIGNATURE_TEST)
          .where(RECORD_SIGNATURE_TEST.ID.eq(record.id))
          .fetchOne()

        if (result == null) {
          throw IllegalStateException("No record found with ID ${record.id} - database update may have failed")
        }

        result
      }
    }
  }

  @Test
  fun `different records with potentially colliding values have different signatures`() {
    // This test verifies that the Length-Value encoding prevents signature collisions.
    // Without LV encoding, these two records could have the same signature:
    // Record 1: name="a", created_by="bc"  -> concatenation = "abc"
    // Record 2: name="ab", created_by="c"  -> concatenation = "abc"
    // With LV encoding, they have different signatures because each value is prefixed with its length.
    
    // 1. Insert first record with name="a" and created_by="bc"
    val record1 = transacter.transaction { (ctx) ->
      ctx.newRecord(RECORD_SIGNATURE_TEST).apply {
        this.name = "a"
        this.createdBy = "bc"
        this.binaryData = "data1".toByteArray()
      }.also { it.store() }
    }
    
    // 2. Insert second record with name="ab" and created_by="c"
    val record2 = transacter.transaction { (ctx) ->
      ctx.newRecord(RECORD_SIGNATURE_TEST).apply {
        this.name = "ab"
        this.createdBy = "c"
        this.binaryData = "data1".toByteArray()
      }.also { it.store() }
    }
    
    // 3. Verify that the signatures are different
    assertNotEquals(
      record1.recordSignature?.toList(),
      record2.recordSignature?.toList(),
      "Records with different values but same concatenation should have different signatures"
    )
    
    // 4. Verify both records can be read back successfully (signatures are valid)
    transacter.transaction { (ctx) ->
      val retrieved1 = ctx.selectFrom(RECORD_SIGNATURE_TEST)
        .where(RECORD_SIGNATURE_TEST.ID.eq(record1.id))
        .fetchOne()!!
      assertNotNull(retrieved1.recordSignature)
      
      val retrieved2 = ctx.selectFrom(RECORD_SIGNATURE_TEST)
        .where(RECORD_SIGNATURE_TEST.ID.eq(record2.id))
        .fetchOne()!!
      assertNotNull(retrieved2.recordSignature)
    }
  }

  @Test
  fun `null values in different positions have different signatures`() {
    // 1. Insert first record with updatedBy null and binaryData "a"
    val record1 = transacter.transaction { (ctx) ->
      ctx.newRecord(RECORD_SIGNATURE_TEST).apply {
        this.name = "foo"
        this.createdBy = "user1" 
        this.updatedBy = null
        this.binaryData = "a".toByteArray()
      }.also { it.store() }
    }
    
    // 2. Insert second record with updatedBy "a" and binaryData null
    val record2 = transacter.transaction { (ctx) ->
      ctx.newRecord(RECORD_SIGNATURE_TEST).apply {
        this.name = "foo" // same value as record1
        this.createdBy = "user1" // same value as record1
        this.updatedBy = "a" // null > a from record1
        this.binaryData = null // a > null from record1
      }.also { it.store() }
    }
    
    // 3. Verify that the signatures are different
    assertNotEquals(
      record1.recordSignature?.toList(),
      record2.recordSignature?.toList(),
      "Records with null values in different positions should have different signatures"
    )
    
    // 4. Verify both records can be read back successfully (signatures are valid)
    transacter.transaction { (ctx) ->
      val retrieved1 = ctx.selectFrom(RECORD_SIGNATURE_TEST)
        .where(RECORD_SIGNATURE_TEST.ID.eq(record1.id))
        .fetchOne()!!
      assertNotNull(retrieved1.recordSignature)
      
      val retrieved2 = ctx.selectFrom(RECORD_SIGNATURE_TEST)
        .where(RECORD_SIGNATURE_TEST.ID.eq(record2.id))
        .fetchOne()!!
      assertNotNull(retrieved2.recordSignature)

      // assert these two records create 2 different signatures
      assertNotEquals(retrieved1.recordSignature, retrieved2.recordSignature)
    }
  }

  @Test
  fun `throws error when signature is updated to null`() {
    // 1. insert new record
    val record = transacter.transaction { (ctx) ->
      ctx.newRecord(RECORD_SIGNATURE_TEST).apply {
        this.name = "test"
        this.createdBy = "foo"
        this.binaryData = "some-data".toByteArray()
      }.also { it.store() }
    }

    // 2. use raw JDBC connection to set signature of the existing record to null
    dataSourceService.dataSource.connection.use { connection ->
      connection.autoCommit = false
      try {
        // Use plain SQL to avoid jOOQ schema mapping issues
        connection.prepareStatement(
          """
          UPDATE misk_jooq_testing_writer.record_signature_test SET record_signature = NULL WHERE id = ?
         """,
        ).use { stmt ->
          stmt.setLong(1, record.id!!)
          stmt.execute()
        }
        connection.commit()
      } catch (e: Exception) {
        connection.rollback()
        throw e
      } finally {
        connection.autoCommit = true
      }
    }

    // 3. should throw when trying to verify on read with the listener-enabled transacter
    assertThrows<DataIntegrityException> {
      transacter.transaction { (ctx) ->
        ctx.selectFrom(RECORD_SIGNATURE_TEST)
          .where(RECORD_SIGNATURE_TEST.ID.eq(record.id))
          .fetchOne()!!
      }
    }
  }
}
