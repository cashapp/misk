@file:JvmName("GidGenerator")

package misk.hibernate

import org.hibernate.HibernateException
import org.hibernate.MappingException
import org.hibernate.dialect.Dialect
import org.hibernate.engine.jdbc.spi.JdbcServices
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.AbstractPostInsertGenerator
import org.hibernate.id.Configurable
import org.hibernate.id.PostInsertIdentityPersister
import org.hibernate.id.insert.Binder
import org.hibernate.id.insert.IdentifierGeneratingInsert
import org.hibernate.id.insert.InsertGeneratedIdentifierDelegate
import org.hibernate.persister.entity.SingleTableEntityPersister
import org.hibernate.pretty.MessageHelper
import org.hibernate.service.ServiceRegistry
import org.hibernate.type.Type
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.Properties

@Suppress("RedundantVisibilityModifier", "unused")
public class GidGenerator : AbstractPostInsertGenerator(), Configurable {
  private lateinit var rootColumn: String

  @Throws(MappingException::class)
  override fun configure(type: Type, params: Properties, serviceRegistry: ServiceRegistry) {
    this.rootColumn = params.getProperty("rootColumn")
    checkNotNull(rootColumn)
  }

  @Throws(HibernateException::class)
  override fun getInsertGeneratedIdentifierDelegate(
    persister: PostInsertIdentityPersister,
    dialect: Dialect,
    isGetGeneratedKeysEnabled: Boolean
  ): InsertGeneratedIdentifierDelegate {
    return GetGeneratedKeysDelegate(persister, dialect, rootColumn)
  }

  private class GetGeneratedKeysDelegate(
    persister: PostInsertIdentityPersister,
    val dialect: Dialect,
    val rootColumn: String
  ) : InsertGeneratedIdentifierDelegate {
    private val persister: SingleTableEntityPersister

    init {
      require(persister is SingleTableEntityPersister) { "Single table entities supported only" }
      this.persister = persister
    }

    override fun prepareIdentifierGeneratingInsert(): IdentifierGeneratingInsert {
      val insert = IdentifierGeneratingWithParentInsert(dialect, rootColumn)
      insert.addIdentityColumn(persister.rootTableKeyColumnNames[0])
      return insert
    }

    override fun performInsert(
      insertSQL: String,
      session: SharedSessionContractImplementor,
      binder: Binder
    ): Serializable {
      @Suppress("UNCHECKED_CAST")
      val entity = binder.entity as DbChild<Nothing, Nothing>
      // Square service container supports pre-assigning the gid by the app in which case we don't
      // generate a new autoinc value and instead just use what's there already. We use this for
      // shard migrations where we want to keep the same row id. Hopefully we never need to use that
      // for our Misk apps, if we do then we should port performIdentifierPresetInsert from
      // CidGenerator.java in the service container.
      return performIdentityGeneratingInsert(insertSQL, session, binder, entity)
    }

    private fun performIdentityGeneratingInsert(
      insertSQL: String,
      session: SharedSessionContractImplementor,
      binder: Binder,
      entity: DbChild<Nothing, Nothing>
    ): Serializable {
      val parentId = entity.rootId

      try {
        // prepare and execute the insert
        val insert = prepare(insertSQL, session)
        try {
          binder.bindValues(insert)
          insert.setLong(insert.parameterMetaData.parameterCount, parentId.id)
          return executeAndExtract(parentId, insert, session)
        } finally {
          releaseStatement(insert, session)
        }
      } catch (sqle: SQLException) {
        throw session.factory.serviceRegistry.getService(
            JdbcServices::class.java).sqlExceptionHelper.convert(
            sqle,
            "could not insert: " + MessageHelper.infoString(persister),
            insertSQL
        )
      }
    }

    @Throws(SQLException::class)
    private fun releaseStatement(
      insert: PreparedStatement,
      session: SharedSessionContractImplementor
    ) {
      session.jdbcCoordinator.logicalConnection.resourceRegistry.release(insert)
      session.jdbcCoordinator.afterStatementExecution()
    }

    @Throws(SQLException::class)
    private fun prepare(
      insertSQL: String,
      session: SharedSessionContractImplementor
    ): PreparedStatement {
      return session.jdbcCoordinator.statementPreparer
          .prepareStatement(insertSQL, PreparedStatement.RETURN_GENERATED_KEYS)
    }

    @Throws(SQLException::class)
    private fun executeAndExtract(
      parentId: Id<Nothing>,
      insert: PreparedStatement,
      session: SharedSessionContractImplementor
    ): Serializable {
      session.jdbcCoordinator.resultSetReturn.executeUpdate(insert)
      var rs: ResultSet? = null
      try {
        rs = insert.generatedKeys
        rs!!.next()
        val id = Id<Nothing>(rs.getLong(1))
        return Gid(parentId, id)
      } finally {
        if (rs != null) {
          session.jdbcCoordinator.logicalConnection.resourceRegistry.release(rs, insert)
          session.jdbcCoordinator.afterStatementExecution()
        }
      }
    }
  }

  private class IdentifierGeneratingWithParentInsert(
    dialect: Dialect,
    private val rootColumn: String
  ) : IdentifierGeneratingInsert(dialect) {
    private var rootColumnAdded = false

    override fun toStatementString(): String {
      // Make sure the parent column is the last column because the actual entity being saved
      // will always get set to the first columns in the PreparedStatement.
      if (!rootColumnAdded) {
        addColumn(rootColumn)
        rootColumnAdded = true
      }
      return super.toStatementString()
    }
  }
}
