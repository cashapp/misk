package misk.hibernate

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import java.math.BigInteger
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashSet
import java.util.function.Function
import java.util.stream.Collectors
import javax.inject.Inject

import com.google.common.base.Preconditions.checkNotNull
import com.google.common.base.Preconditions.checkState
import java.util.logging.Logger
import java.util.stream.Collectors.joining

/**
 * Use the BulkShardMigrator to update rows in such a way that they need to migrate to another
 * shard. The BulkShardMigrator works on both MySQL and Vitess.
 *
 * On MySQL it will issue simple UPDATE statements.
 *
 * On Vitess it will copy the rows between shards using a SELECT and a batched INSERT statements.
 * The mutations are applied to the result set in memory between the SELECT and the batch INSERT.
 */
class BulkShardMigrator<R : DbRoot<R>, C : DbChild<R, C>>(
  private val persistenceMetadata: PersistenceMetadata,
  private val transacter: Transacter,
  private val schemaMunger: SchemaMunger,
  private val shards: Shards,
  private val rootClass: Class<R>,
  private val childClass: Class<C>
) {

  private val mutations = ArrayList<Mutation>()
  private var where: String? = null
  private var parameters: List<*>? = null
  private var mutationsByColumnName: ImmutableMap<String, Mutation>? = null
  private var rootColumnName = "customer_id"
  private var sourceRoot: Id<R>? = null
  private var targetRoot: Id<R>? = null
  private var postInsertPreDeleteStep: PostInsertPreDeleteStep<C>? = null
  private var batched: Boolean = false
  private var latestBatchOnly: Boolean = false
  private var batchSize = DEFAULT_BATCH_SIZE

  init {
    if (DbTimestampedEntity::class.java!!.isAssignableFrom(childClass)) {
      now("updated_at")
    }
  }

  fun rootColumn(columnName: String): BulkShardMigrator<R, C> {
    // TODO we can eventually pull this automatically out of PersistenceMetaData but we need an annotation for it
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

  operator fun set(column: String, valueMapper: Function<Any, Any>): BulkShardMigrator<R, C> {
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
    checkState(batched)
    this.latestBatchOnly = latestBatchOnly
    return this
  }

  fun batchSize(batchSize: Int): BulkShardMigrator<R, C> {
    checkState(batched)
    this.batchSize = batchSize
    return this
  }

  /**
   * Sets a function to run after records are copied but before they are deleted.
   * For same-shard migrations, this runs in the same transaction (after delete, but before
   * delete commits).
   *
   * If an action needs to be taken on the set of copied entities, but on a retry the entities may
   * already be deleted, this is a safe place to perform the action.
   *
   * The deletedIds parameter contains a list of ids deleted in this batch,
   * so that work can be performed on just this subset.
   */
  fun postInsertPreDeleteStep(
    postInsertPreDeleteStep: PostInsertPreDeleteStep<C>
  ): BulkShardMigrator<R, C> {
    this.postInsertPreDeleteStep = postInsertPreDeleteStep
    return this
  }

  private fun maybeRunPostInsertPreDelete(deletedIds: Set<Id<C>>) {
    if (postInsertPreDeleteStep != null) {
      postInsertPreDeleteStep!!.run(deletedIds)
    }
  }

  @JvmOverloads fun execute(session: Session?, insertIgnore: Boolean = false) {
    if (session == null) {
      execute(insertIgnore)
      return
    }

    checkNotNull<Id<R>>(targetRoot, "You have to specify entity root target")
    checkNotNull<Id<R>>(sourceRoot, "You have to specify entity root source")

    session.useConnection { connection ->
      connection.prepareStatement(schemaMunger.mungeSql(
          String.format("SELECT %s FROM %s WHERE %s",
              columnNames().joinToString(", "),
              tableName(),
              where))).use { select ->
        bindWhereClause(select)
        val resultSet = select.executeQuery()
        if (isShardLocal(session)) {
          // If we're moving intra-shard we should delete the target rows first so we don't get
          // ID conflicts. This is fine because we're in a shard local transaction. We won't
          // "lose" the rows.
          delete(session, connection)
          val deletedIds = insert(session, connection, resultSet, insertIgnore)
          maybeRunPostInsertPreDelete(deletedIds)
        } else {
          // If we're moving cross-shard we're going to have a cross shard transaction (2pc or
          // best effort, still to be decided). We clean up the records afterwards so we don't
          // drop them on the floor if we have a mixed transaction outcome. A partial failure
          // may cause the same ID to appear in multiple shards which may cause errors and will
          // require an operator to clean up.
          val deletedIds = insert(session, connection, resultSet, insertIgnore)
          maybeRunPostInsertPreDelete(deletedIds)
          delete(session, connection)
        }
      }
    }
  }

  @JvmOverloads fun execute(insertIgnore: Boolean = false) {
    checkNotNull<Id<R>>(targetRoot, "You have to specify entity root target")
    checkNotNull<Id<R>>(sourceRoot, "You have to specify entity root source")

    var count: Int

    do {
      count = executeBatch(insertIgnore)
      if (!batched || latestBatchOnly) {
        break
      }
      // The batch was filled so there may be more entities to transfer.
    } while (count == batchSize)
  }

  /** Migrates one batch. Returns the number of records migrated.  */
  private fun executeBatch(insertIgnore: Boolean): Int {
    val tableName = tableName()
    val shardLocalCount = transacter.transaction { session ->
      if (isShardLocal(session)) {
        // We need to do the work in the same transaction as the isShardLocal check.
        // If the transaction fails due to a shard split,
        // this transaction will retry and we will recompute isShardLocal.
        val sourceRecords = loadSourceRecords(session)
        if (sourceRecords.isEmpty()) {
          return 0
        }

        logger.info {
          "Bulk migrating (same shard) ${sourceRecords.size} entities for table $tableName"
        }

        delete(session, sourceRecords.keys)
        session.useConnection { connection ->
          insert(session, connection, sourceRecords, ImmutableSet.of(), insertIgnore)
        }
        maybeRunPostInsertPreDelete(idsFromLongs(sourceRecords.keys))
        return sourceRecords.size
      }
      return -1
    }

    if (shardLocalCount != -1) {
      return shardLocalCount
    }

    // Above code might be called in recursive PostInsertPreDeleteSteps, which is safe when using
    // the same source and root since they are still shard local. But if not shard local, we should
    // not have recursive transactions.
    checkState(!transacter.inTransaction)

    // When data is being transferred between two shards, to avoid losing data, first insert,
    // then delete. There is a potential for duplicate IDs being loaded between these calls but we
    // try to keep it short with small batches.
    val idsToDelete = transacter.transaction { session ->
      val sourceRecords = loadSourceRecords(session)
      if (sourceRecords.isEmpty()) {
        return sourceRecords.keys
      }

      val existingIds = TypedQuery<BigInteger>(
          session.createSQLQuery(
              schemaMunger.mungeSql(
                  "SELECT id FROM $tableName WHERE $rootColumnName = :target AND id IN (:ids)"
              )
              .setParameter("target", targetRoot!!.id)
              .setParameterList("ids", sourceRecords.keys)
              .list()
              .stream()
              .map{ it -> it.id }
              .collect(Collectors.toSet<T>())

      logger.info {
        "Bulk migrating (distinct shard) ${sourceRecords.size} entities for table $tableName"
      }

      session.useConnection { connection ->
        insert(session, connection, sourceRecords, existingIds, insertIgnore)
      }
      sourceRecords.keys.toInt()
    }

    // Delete only actually copied ids in case there is a new entity inserted on the source after
    // the insert was done.
    if (!idsToDelete.isEmpty()) {
      maybeRunPostInsertPreDelete(idsFromLongs(idsToDelete))
      transacter.transaction { session -> delete(session, idsToDelete) }
    }
    return idsToDelete.size
  }

  private fun loadSourceRecords(session: Session): Map<Long, List<Any>> {
    val map = HashMap<Long, List<Any>>()
    val columnNames = columnNames().asList()
    session.useConnection { connection ->
      val limit = if (batched) {
        "ORDER BY id DESC LIMIT $batchSize"
      } else {
        ""
      }
      connection.prepareStatement(schemaMunger.mungeSql(
          String.format("SELECT %s FROM %s WHERE %s %s",
              columnNames.joinToString(", "),
              tableName(),
              where,
              limit))).use { select ->
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

  private fun isShardLocal(session: Session?): Boolean {
    checkNotNull<Id<R>>(targetRoot)
    checkNotNull<Id<R>>(sourceRoot)
    val tableName = persistenceMetadata.getTableName(rootClass)
    val sourceShard = shards.shardOf(session, tableName, sourceRoot!!.id)
    val targetShard = shards.shardOf(session, tableName, targetRoot!!.id)
    return sourceShard == targetShard
  }

  private fun tableName(): String {
    return persistenceMetadata.getTableName(childClass)
  }

  @Throws(SQLException::class)
  private fun insert(
    session: Session?, connection: Connection, resultSet: ResultSet,
    insertIgnore: Boolean
  ): Set<Id<C>> {
    val statement = "INSERT " + (if (insertIgnore) "IGNORE " else "") + "INTO %s (%s) VALUES (%s)"

    val columnNames = columnNames().asList()
    val idsSet = LinkedHashSet<Id<C>>()
    connection.prepareStatement(schemaMunger.mungeSql(
        String.format(statement,
            tableName(),
            columnNames.joinToString(", "),
            columnNames.stream().map { this.insertValueSql(it) }.collect<String, *>(
                joining(", "))))).use { insert ->
      var batches = 0
      while (resultSet.next()) {
        var parameterIndex = 1
        for (i in columnNames.indices) {
          val columnName = columnNames[i]
          val value = resultSet.getObject(i + 1)
          parameterIndex += bindInsert(columnName, insert, parameterIndex, value)
        }
        insert.addBatch()
        idsSet.add(Id.of(resultSet.getLong("id")))
        batches++
      }
      if (batches > 0) {
        insert.executeBatch()
      }
    }
    return idsSet
  }

  @Throws(SQLException::class)
  private fun insert(
    session: Session, connection: Connection,
    resultSet: Map<Long, List<Any>>,
    existingIds: Set<Long>, insertIgnore: Boolean
  ) {
    val statement = "INSERT " + (if (insertIgnore) "IGNORE " else "") + "INTO %s (%s) VALUES (%s)"

    val columnNames = columnNames().asList()
    connection.prepareStatement(schemaMunger.mungeSql(
        String.format(statement,
            tableName(),
            columnNames.joinToString(", "),
            columnNames.stream().map { this.insertValueSql(it) }.collect<String, *>(
                joining(", "))))).use { insert ->
      var batches = 0
      for ((key, columnValues) in resultSet) {
        if (existingIds.contains(key)) {
          continue
        }
        var parameterIndex = 1
        for (i in columnNames.indices) {
          val columnName = columnNames[i]
          val value = columnValues[i]
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

  @Throws(SQLException::class)
  private fun delete(session: Session, connection: Connection) {
    connection.prepareStatement(schemaMunger.mungeSql(
        String.format("DELETE FROM %s WHERE %s", tableName(), where))).use { delete ->
      bindWhereClause(delete)
      delete.executeUpdate()
    }
  }

  private fun delete(session: Session, idsToDelete: Collection<Long>) {
    val tableName = tableName()
    val numRecords = session.createSQLQuery(schemaMunger.mungeSql(
        String.format("DELETE FROM %s "
            + "WHERE %s = :source "
            + "AND id IN (:ids)",
            tableName, rootColumnName)))
        .setParameter("source", sourceRoot!!.id)
        .setParameterList("ids", idsToDelete)
        .executeUpdate()
    if (numRecords != idsToDelete.size) {
      logger.info {
        "Deleted less records than expected from $tableName " +
            "($numRecords < ${idsToDelete.size}) after copying"
      }
    }
  }

  @Throws(SQLException::class)
  private fun bindInsert(
    columnName: String, insert: PreparedStatement, parameterIndex: Int,
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
        throw RuntimeException("Can't infer type of column $columnName for value $value")
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
      mutationsByColumnName = mutations.stream()
          .collect(toImmutableMap(???({ it.columnName() }), identity<T>()))
    }
    return mutationsByColumnName!![columnName]
  }

  @Throws(SQLException::class)
  private fun bindWhereClause(select: PreparedStatement) {
    for (i in parameters!!.indices) {
      select.setObject(i + 1, parameters!![i])
    }
  }

  private fun idsFromLongs(longs: Set<Long>): Set<Id<C>> {
    return longs.stream()
        .map(Function<Long, Any> { of() })
        .collect<Set<Id<C>>, Any>(Collectors.toSet<Any>())
  }

  abstract class Mutation {
    internal abstract fun columnName(): String

    internal open fun insertSql(): String {
      return "?"
    }

    @Throws(SQLException::class)
    internal open fun bindInsert(insert: PreparedStatement, parameterIndex: Int, value: Any): Int {
      insert.setObject(parameterIndex, value)
      return 1
    }
  }

  class SetMutation(private val column: String, private val value: Any) : Mutation() {
    override fun columnName(): String {
      return column
    }

    @Throws(SQLException::class)
    override fun bindInsert(insert: PreparedStatement, parameterIndex: Int, value: Any): Int {
      insert.setObject(parameterIndex, this.value)
      return 1
    }
  }

  class SetMappingMutation(
    private val column: String,
    private val valueMapper: Function<Any, Any>
  ) : Mutation() {

    override fun columnName(): String {
      return column
    }

    @Throws(SQLException::class)
    override fun bindInsert(insert: PreparedStatement, parameterIndex: Int, value: Any): Int {
      insert.setObject(parameterIndex, valueMapper.apply(value))
      return 1
    }
  }

  class NowMutation(private val column: String) : Mutation() {

    override fun columnName(): String {
      return column
    }

    @Throws(SQLException::class)
    override fun bindInsert(insert: PreparedStatement, parameterIndex: Int, value: Any): Int {
      // There's nothing to bind, it all happens in the DB
      return 0
    }

    override fun insertSql(): String {
      return "now()"
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
  }

  interface PostInsertPreDeleteStep<C : EntityGroupChild<*, *>> {
    fun run(deletedIds: Set<Id<C>>)
  }

  class Factory {
    @Inject internal var persistenceMetadata: PersistenceMetadata? = null
    @Inject internal var transacter: Transacter? = null
    @Inject internal var schemaMunger: SchemaMunger? = null
    @Inject internal var shards: Shards? = null

    // Assisted Inject doesn't seem to play well with type parameters :-(
    fun <R : EntityGroupRoot, C : EntityGroupChild<R, C>> create(
      rootClass: Class<R>,
      childClass: Class<C>
    ): BulkShardMigrator<R, C> {
      return BulkShardMigrator(persistenceMetadata, transacter,
          schemaMunger, shards, rootClass, childClass)
    }
  }

  companion object {
    private val logger = Logger.getLogger(BulkShardMigrator<*, *>::class.java)

    var DEFAULT_BATCH_SIZE = 100

    /** Returns a string like `?,?,?` with `size` question marks.  */
    fun questionMarks(size: Int): String {
      val result = StringBuilder()
      for (i in 0 until size) {
        if (i > 0) result.append(",")
        result.append("?")
      }
      return result.toString()
    }
  }
}

