package misk.hibernate.actions

import com.google.inject.Injector
import javax.persistence.Transient
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField
import misk.exceptions.BadRequestException
import misk.hibernate.DbEntity
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
    fun checkQueryMatchesAction(queryClass: String, isDynamicAction: Boolean) {
      val isDynamicQuery = queryClass.endsWith(DatabaseQueryMetadata.DYNAMIC_QUERY_KCLASS_SUFFIX)
      val status = if (isDynamicQuery) "is" else "is not"
      val action =
        if (isDynamicAction) {
          HibernateDatabaseQueryStaticAction::class.simpleName!!
        } else {
          HibernateDatabaseQueryDynamicAction::class.simpleName!!
        }

      if ((isDynamicAction && !isDynamicQuery) || (!isDynamicAction && isDynamicQuery)) {
        throw BadRequestException("[queryClass=$queryClass] $status a DynamicQuery and should be handled by $action")
      }
    }

    /** Find the server-side registration (metadata + authoritative KClasses) for a request's query class */
    fun findDatabaseQueryRegistration(
      registrations: List<HibernateDatabaseQueryRegistration>,
      queryClass: String,
    ): HibernateDatabaseQueryRegistration =
      registrations.find { it.metadata.queryClass == queryClass } ?: throw BadRequestException("Invalid Query Class")

    /** Common to both Dynamic and Static actions, get the Transacter that owns the authorized [entityClass]. */
    fun getTransacterForDatabaseQueryAction(injector: Injector, entityClass: KClass<out DbEntity<*>>): Transacter =
      injector
        .findBindingsByType(Transacter::class.typeLiteral())
        .find { transacterBinding -> transacterBinding.provider.get().entities().contains(entityClass) }
        ?.provider
        ?.get() ?: throw BadRequestException("[dbEntity=${entityClass.simpleName}] has no associated Transacter")

    /** Validate provided Select paths or include all (ignoring some that we can't query on like rootId) */
    fun validateSelectPathsOrDefault(dbEntity: KClass<out DbEntity<*>>, paths: List<String>?): List<String> {
      val invalidSelectPaths = setOf("rootId")
      val dbEntityDeclaredMemberProperties =
        dbEntity.declaredMemberProperties
          .filter { it ->
            !invalidSelectPaths.contains(it.name) &&
              // Because Transient is a Java annotation, we have to check it on the underlying Java
              // field. Searching for it on the KProperty will fail.
              it.javaField?.getAnnotation(Transient::class.java) == null
          }
          .map { it.name }
      return if (paths?.isNotEmpty() == true) {
        if (!dbEntityDeclaredMemberProperties.containsAll(paths)) {
          throw BadRequestException(
            "Invalid Select path does not exist on " +
              "[dbEntity=$dbEntity][invalidPaths=${paths - dbEntityDeclaredMemberProperties}]"
          )
        }
        paths
      } else {
        dbEntityDeclaredMemberProperties
      }
    }
  }
}
