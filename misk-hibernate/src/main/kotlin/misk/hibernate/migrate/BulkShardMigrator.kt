package misk.hibernate.migrate

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import misk.hibernate.DbChild
import misk.hibernate.DbRoot
import misk.hibernate.DbTimestampedEntity
import misk.hibernate.Id
import misk.hibernate.PersistenceMetadata
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.shards
import misk.jdbc.Check
import misk.jdbc.DataSourceType
import misk.logging.getLogger
import misk.vitess.Keyspace
import misk.vitess.Shard
import misk.vitess.Shard.Companion.SINGLE_KEYSPACE
import org.hibernate.SessionFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.util.ArrayList
import java.util.HashMap
import java.util.stream.Collectors.joining
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * BulkShardMigrator facilitates moving of child entities belonging to a source root entity to
 * target root entity in bulk. Source or target entity can either live on the same or different
 * shards.
 *
 * If moving between shards it will copy the rows between shards using a SELECT and a batched INSERT
 * statements. The mutations are applied to the result set in memory between the SELECT and the
 * batch INSERT.
 *
 * An example of moving rows from characters table that is sharded by movie_id from source_id to
 * target_id:
 *
 * bulkShardMigratorFactory.create(transacter, sessionFactory, DbMovie::class, DbCharacter::class)
 *    .rootColumn("movie_id")
 *    .source(sourceId)
 *    .target(targetId)
 *    .execute()
 */
class BulkShardMigrator<R : DbRoot<R>, C : DbChild<R, C>> private constructor(
  private val rootClass: KClass<R>,
  sessionFactory: SessionFactory,
  private val transacter: Transacter,
  private val childClass: KClass<C>
) {

  private val persistenceMetadata: PersistenceMetadata
  private val keyspace: Keyspace

  private val mutations = ArrayList<Mutation>()
  private var where: String? = null
  private var parameters: List<*>? = null
  private var rootColumnName = "customer_id"
  private var sourceRoot: Id<R>? = null
  private var targetRoot: Id<R>? = null
  private var batched: Boolean = true
  private var latestBatchOnly: Boolean = false
  private var batchSize = 100
  private var mutationsByColumnName: Map<String, Mutation>? = null

  init {
    if (childClass.isSubclassOf(DbTimestampedEntity::class)) {
      now("updated_at")
    }
    this.persistenceMetadata = PersistenceMetadata(sessionFactory)

    // TODO(alihussain): cache this immediately after in a follow up!
    val annotation = rootClass.java.getAnnotation(misk.hibernate.annotation.Keyspace::class.java)
      ?: throw NullPointerException(
        "$rootClass requires the Keyspace annotation to use BulkShardMigrator. " +
          "If using with MySQL annotate with @Keyspace(\"keyspace\")"
      )
    this.keyspace = Keyspace(annotation.value)
  }

  fun rootColumn(columnName: String): BulkShardMigrator<R, C> {
    // TODO(alihussain): get this from annotation immediately after in a follow up!
    rootColumnName = columnName
    return this
  }

  fun source(sourceRoot: Id<R>): BulkShardMigrator<R, C> {
    this.sourceRoot = sourceRoot
    // This is the "default" where clause if you specify a source, if you specify your own where
    // clause you need to include the source yourself.
    if (where == null) {
      where("$rootColumnName = ?", sourceRoot.id)
    }
    return this
  }

  fun target(targetRoot: Id<R>): BulkShardMigrator<R, C> {
    this.targetRoot = targetRoot
    set(rootColumnName, targetRoot.id)
    return this
  }

  operator fun set(column: String, value: Any): BulkShardMigrator<R, C> {
    mutations.add(SetMutation(column, value))
    return this
  }

  operator fun set(
    column: String,
    valueMapper: java.util.function.Function<Any, Any>
  ): BulkShardMigrator<R, C> {
    mutations.add(SetMappingMutation(column, valueMapper))
    return this
  }

  fun now(column: String): BulkShardMigrator<R, C> {
    mutations.add(NowMutation(column))
    return this
  }

  operator fun inc(column: String): BulkShardMigrator<R, C> {
    mutations.add(IncMutation(column))
    return this
  }

  fun where(where: String, vararg parameters: Any): BulkShardMigrator<R, C> {
    return where(where, ImmutableList.copyOf(parameters))
  }

  fun where(where: String, parameters: List<*>): BulkShardMigrator<R, C> {
    this.where = where
    this.parameters = parameters
    return this
  }

  fun batched(): BulkShardMigrator<R, C> {
    this.batched = true
    return this
  }

  fun latestBatchOnly(latestBatchOnly: Boolean): BulkShardMigrator<R, C> {
    check(batched)
    this.latestBatchOnly = latestBatchOnly
    return this
  }

  fun batchSize(batchSize: Int): BulkShardMigrator<R, C> {
    check(batched)
    this.batchSize = batchSize
    return this
  }

  fun execute(insertIgnore: Boolean = false) {
    checkNotNull(targetRoot) { "You have to specify entity root target" }
    checkNotNull(sourceRoot) { "You have to specify entity root source" }

    if (transacter.config().type == DataSourceType.TIDB) {
      return executeUnshardedMigration()
    }

    var count: Int
    do {
      count = executeBatch(insertIgnore)
      if (!batched || latestBatchOnly) {
        break
      }
      // The batch was filled so there may be more entities to transfer.
    } while (count == batchSize)
  }

  /** Migrates entity in an unsharded store by doing a bulk update */
  private fun executeUnshardedMigration() {
    check(!transacter.inTransaction)

    val tableName = tableName()
    val setColumns = mutations.stream()
      .map { mutation -> mutation.updateSql() }
      .collect(joining(","))

    logger.info("Bulk migrating in ${transacter.config().type} entities for table $tableName")
    transacter.transaction { session ->
      session.hibernateSession.doWork { connection ->
        run {
          val update = """
          UPDATE ${tableName()}
          SET $setColumns
          WHERE $where
        """.trimIndent()
          val updateStatement = connection.prepareStatement(update)
          var setParametersCount = 0
          mutations
            .filter { it.isParameterized() }
            .forEachIndexed { index, mutation ->
              // statements indexes start from 1
              mutation.bindUpdate(updateStatement, index + 1)
              setParametersCount += 1
            }
          bindWhereClause(updateStatement, setParametersCount)
          updateStatement.executeUpdate()
        }
      }
    }
  }

  /** Migrates one batch. Returns the number of records migrated.  */
  private fun executeBatch(insertIgnore: Boolean): Int {
    check(!transacter.inTransaction)

    val tableName = tableName()
    logger.info { "Starting BulkShardMigrator-$tableName-$sourceRoot-$targetRoot batch" }

    if (isShardLocal()) {
      // We need to do the work in the same transaction as the isShardLocal check.
      // If the transaction fails due to a shard split,
      // this transaction will retry and we will recompute isShardLocal.
      return transacter.transaction { session ->
        session.withoutChecks(Check.COWRITE) {
          val sourceRecords = loadSourceRecords(session)
          if (sourceRecords.isEmpty()) {
            0
          } else {
            logger.info(
              "Bulk migrating (same shard) ${sourceRecords.size} entities for table" +
                " $tableName"
            )
            delete(session, sourceRecords.keys)
            session.hibernateSession.doWork { connection ->
              insert(connection, sourceRecords, setOf(), insertIgnore)
            }
            sourceRecords.size
          }
        }
      }
    }

    // When data is being transferred between two shards, to avoid losing data, first insert,
    // then delete. There is a potential for duplicate IDs being loaded between these calls but we
    // try to keep it short with small batches.
    val idsToDelete = transacter.transaction { session ->
      val sourceRecords = loadSourceRecords(session)
      if (sourceRecords.isEmpty()) {
        setOf()
      } else {
        val existingIds = session.hibernateSession.createNativeQuery(
          "SELECT id FROM $tableName WHERE $rootColumnName = :target AND id IN (:ids)"
        ).setParameter("target", targetRoot!!.id)
          .setParameterList("ids", sourceRecords.keys)
          .resultList
          .map { (it as Id<*>).id }
          .toSet()

        logger.info(
          "Bulk migrating (distinct shard) ${sourceRecords.size} entities for table $tableName"
        )

        session.hibernateSession.doWork { connection ->
          insert(connection, sourceRecords, existingIds, insertIgnore)
        }
        sourceRecords.keys
      }
    }

    // Delete only actually copied ids in case there is a new entity inserted on the source after
    // the insert was done.
    if (!idsToDelete.isEmpty()) {
      transacter.transaction { session -> delete(session, idsToDelete) }
    }
    return idsToDelete.size
  }

  private fun loadSourceRecords(session: Session): Map<Long, List<Any>> {
    val map = HashMap<Long, List<Any>>()
    val columnNames = columnNames().asList()
    session.hibernateSession.doWork { connection ->
      val limit = if (batched)
        "ORDER BY id DESC LIMIT $batchSize"
      else
        ""
      connection.prepareStatement(
        "SELECT ${columnNames.joinToString(", ")} FROM ${tableName()} WHERE $where $limit"
      ).use { select ->
        bindWhereClause(select)
        val resultSet = select.executeQuery()
        while (resultSet.next()) {
          val id = resultSet.getLong("id")
          val values = ArrayList<Any>()
          for (i in columnNames.indices) {
            values.add(resultSet.getObject(i + 1))
          }
          map[id] = values
        }
      }
    }
    return map
  }

  private fun isShardLocal(): Boolean {
    val sourceShard = getShard(sourceRoot!!)
    val targetShard = getShard(targetRoot!!)
    return sourceShard == targetShard
  }

  private fun getShard(id: Id<R>): Shard {
    val shards = transacter.shards()
    return shards.find {
      (it.keyspace == keyspace || it.keyspace == SINGLE_KEYSPACE) && it.contains(id.shardKey())
    } ?: throw NoSuchElementException(
      "No shard found for [class=$rootClass][id=$id]" +
        "[keyspace=$keyspace][shardKey=${id.shardKey()}] out of [shards=$shards]"
    )
  }

  private fun tableName(): String {
    return persistenceMetadata.getTableName(childClass)
  }

  private fun insert(
    connection: Connection,
    resultSet: Map<Long, List<Any>>,
    existingIds: Set<Long>,
    insertIgnore: Boolean
  ) {
    val columnNames = columnNames().asList()
    val columnValues = columnNames().stream().map { insertValueSql(it) }.collect(joining(", "))
    val statement = """
      INSERT ${(if (insertIgnore) "IGNORE " else "")}
      INTO ${tableName()} (${columnNames.joinToString(", ")})
      VALUES ($columnValues)
      """.trimIndent()

    connection.prepareStatement(statement)
      .use { insert ->
        var batches = 0
        for ((key, resultColumnValues) in resultSet) {
          if (existingIds.contains(key)) {
            continue
          }
          var parameterIndex = 1
          for (i in columnNames.indices) {
            val columnName = columnNames[i]
            val value = resultColumnValues[i]
            parameterIndex += bindInsert(columnName, insert, parameterIndex, value)
          }
          insert.addBatch()
          batches++
        }
        if (batches > 0) {
          insert.executeBatch()
        }
      }
  }

  private fun columnNames(): ImmutableSet<String> {
    return persistenceMetadata.getColumnNames(childClass)
  }

  private fun delete(session: Session, idsToDelete: Collection<Long>) {
    val tableName = tableName()
    val numRecords = session.hibernateSession.createNativeQuery(
      """
          DELETE FROM $tableName
          WHERE $rootColumnName = :source
          AND id IN (:ids)
        """.trimIndent()
    ).setParameter("source", sourceRoot!!.id)
      .setParameterList("ids", idsToDelete)
      .executeUpdate()
    if (numRecords != idsToDelete.size) {
      logger.info(
        "Deleted less records than expected from %s (%s < %s) after copying",
        tableName, numRecords, idsToDelete.size
      )
    }
  }

  @Throws(SQLException::class)
  private fun bindInsert(
    columnName: String,
    insert: PreparedStatement,
    parameterIndex: Int,
    value: Any
  ): Int {
    val mutation = mutationNamed(columnName)
    if (mutation != null) {
      return mutation.bindInsert(insert, parameterIndex, value)
    } else {
      try {
        if (value is ByteArray) {
          // TODO Handle auto inference of byte[] in VitessPreparedStatement
          insert.setBytes(parameterIndex, value)
        } else {
          insert.setObject(parameterIndex, value)
        }
      } catch (e: SQLException) {
        throw RuntimeException(
          String.format("Can't infer type of column %s for value %s", columnName, value)
        )
      }

      return 1
    }
  }

  private fun insertValueSql(columnName: String): String {
    val mutation = mutationNamed(columnName)
    return mutation?.insertSql() ?: "?"
  }

  private fun mutationNamed(columnName: String): Mutation? {
    if (mutationsByColumnName == null) {
      mutationsByColumnName = mutations.map {
        it.columnName() to it
      }.toMap()
    }
    return mutationsByColumnName!![columnName]
  }

  @Throws(SQLException::class)
  private fun bindWhereClause(select: PreparedStatement) {
    bindWhereClause(select, 0)
  }

  @Throws(SQLException::class)
  private fun bindWhereClause(select: PreparedStatement, startIndex: Int) {
    for (i in parameters!!.indices) {
      select.setObject(startIndex + i + 1, parameters!![i])
    }
  }

  abstract class Mutation {
    internal abstract fun columnName(): String

    open fun insertSql(): String {
      return "?"
    }

    open fun bindInsert(insert: PreparedStatement, parameterIndex: Int, value: Any): Int {
      insert.setObject(parameterIndex, value)
      return 1
    }

    abstract fun bindUpdate(update: PreparedStatement, parameterIndex: Int): Int

    open fun updateSql(): String {
      return "${columnName()} = ?"
    }

    internal fun isParameterized(): Boolean {
      return updateSql().contains('?')
    }
  }

  class SetMutation(private val column: String, private val value: Any) : Mutation() {

    override fun columnName(): String {
      return column
    }

    override fun bindInsert(insert: PreparedStatement, parameterIndex: Int, value: Any): Int {
      insert.setObject(parameterIndex, this.value)
      return 1
    }

    override fun bindUpdate(update: PreparedStatement, parameterIndex: Int): Int {
      update.setObject(parameterIndex, value)
      return 1
    }
  }

  class SetMappingMutation(
    private val column: String,
    private val valueMapper: java.util.function.Function<Any, Any>
  ) : Mutation() {

    override fun columnName(): String {
      return column
    }

    @Throws(SQLException::class)
    override fun bindInsert(insert: PreparedStatement, parameterIndex: Int, value: Any): Int {
      insert.setObject(parameterIndex, valueMapper.apply(value))
      return 1
    }

    override fun bindUpdate(update: PreparedStatement, parameterIndex: Int): Int {
      throw UnsupportedOperationException("Cannot apply updates using SetMappingMutation")
    }
  }

  class NowMutation(private val column: String) : Mutation() {

    override fun columnName(): String {
      return column
    }

    override fun bindInsert(insert: PreparedStatement, parameterIndex: Int, value: Any): Int {
      // There's nothing to bind, it all happens in the DB
      return 0
    }

    override fun bindUpdate(update: PreparedStatement, parameterIndex: Int): Int {
      // There's nothing to bind, column modification in 'set' expression
      return 0
    }

    override fun insertSql(): String {
      return "now()"
    }

    override fun updateSql(): String {
      return "$column = now()"
    }
  }

  class IncMutation(private val column: String) : Mutation() {

    override fun columnName(): String {
      return column
    }

    @Throws(SQLException::class)
    override fun bindInsert(insert: PreparedStatement, parameterIndex: Int, value: Any): Int {
      insert.setObject(parameterIndex, (value as Number).toLong() + 1)
      return 1
    }

    override fun bindUpdate(update: PreparedStatement, parameterIndex: Int): Int {
      // There's nothing to bind, column modification in 'set' expression
      return 0
    }

    override fun updateSql(): String {
      return "$column = $column + 1"
    }
  }

  @Singleton
  class Factory @Inject constructor() {
    fun <R : DbRoot<R>, C : DbChild<R, C>> create(
      transacter: Transacter,
      sessionFactory: SessionFactory,
      rootClass: KClass<R>,
      childClass: KClass<C>
    ): BulkShardMigrator<R, C> {
      return BulkShardMigrator(
        rootClass,
        sessionFactory,
        transacter,
        childClass
      )
    }
  }

  companion object {
    private val logger = getLogger<BulkShardMigrator<*, *>>()
  }
}
