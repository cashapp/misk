package misk.hibernate.actions

import com.google.inject.Injector
import misk.exceptions.BadRequestException
import misk.hibernate.Transacter
import misk.inject.KAbstractModule
import misk.inject.typeLiteral
import misk.web.WebActionModule
import misk.web.metadata.database.DatabaseQueryMetadata

/** Install Hibernate specific Web Actions to support Database Query admin dashboard tab */
internal class HibernateDatabaseQueryWebActionModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<HibernateDatabaseQueryDynamicAction>())
    install(WebActionModule.create<HibernateDatabaseQueryStaticAction>())
  }

  companion object {
    /** Throw a readable error if a Dynamic Query is sent to the Static Web Action or vice versa */
    fun checkDynamicQuery(queryClass: String, isDynamicAction: Boolean) {
      val isDynamicQuery = queryClass.endsWith(DatabaseQueryMetadata.DYNAMIC_QUERY_KCLASS_SUFFIX)
      val status = if (isDynamicQuery) "is" else "is not"
      val action = if (isDynamicAction) {
        HibernateDatabaseQueryStaticAction::class.simpleName!!
      } else {
        HibernateDatabaseQueryDynamicAction::class.simpleName!!
      }

      if ((isDynamicAction && !isDynamicQuery) || (!isDynamicAction && isDynamicQuery)) {
        throw BadRequestException(
            "[queryClass=$queryClass] $status a DynamicQuery and should be handled by $action"
        )
      }
    }

    /** Find the corresponding DatabaseQueryMetadata for a request */
    fun findDatabaseQueryMetadata(
      databaseQueryMetadata: List<DatabaseQueryMetadata>,
      queryClass: String
    ): DatabaseQueryMetadata = databaseQueryMetadata.find {
      it.queryClass == queryClass
    } ?: throw BadRequestException("Invalid Query Class")

    /** Common to both Dynamic and Static actions, get Transacter for a given request's DbEntity */
    fun getTransacterForDatabaseQueryAction(
      injector: Injector,
      metadata: DatabaseQueryMetadata
    ): Transacter = injector.findBindingsByType(Transacter::class.typeLiteral())
        .find { transacterBinding ->
          transacterBinding.provider.get().entities().map { it.simpleName!! }
              .contains(metadata.entityClass)
        }?.provider?.get() ?: throw BadRequestException(
        "[dbEntity=${metadata.entityClass}] has no associated Transacter")
  }
}