package misk.hibernate

import com.google.inject.Key
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.name.Names
import misk.hibernate.actions.DatabaseQueryMetadataProvider
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule
import misk.hibernate.actions.HibernateQuery
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.metadata.database.DatabaseQueryMetadata
import misk.web.metadata.database.NoAdminDashboardDatabaseAccess
import org.hibernate.event.spi.EventType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * Binds hibernate entities and event listeners intended for the [Transacter] annotated by
 * [qualifier].
 */
abstract class HibernateEntityModule(
  private val qualifier: KClass<out Annotation>
) : KAbstractModule() {

  abstract fun configureHibernate()

  override fun configure() {
    // Initialize empty sets for our multibindings.
    newMultibinder<AccessAnnotationEntry>()
    newMultibinder<DatabaseQueryMetadata>()
    newMultibinder<HibernateEntity>(qualifier)
    newMultibinder<HibernateQuery>()
    newMultibinder<ListenerRegistration>(qualifier)

    configureHibernate()
  }

  /** Provide a helper function to discoverably install the necessary module to enable the Database dashboard tab */
  protected fun installHibernateAdminDashboardWebActions() {
    install(HibernateDatabaseQueryWebActionModule())
  }

  protected fun addEntities(vararg entities: KClass<out DbEntity<*>>) {
    entities.forEach { entity ->
      addEntity(dbEntityClass = entity)
    }
  }

  /** Install entities with an AccessAnnotation to set run query permissions for Database-Query tab */
  protected fun addEntities(
    accessAnnotationClass: KClass<out Annotation>,
    vararg entities: KClass<out DbEntity<*>>
  ) {
    entities.forEach { entity ->
      addEntity(dbEntityClass = entity, accessAnnotationClass = accessAnnotationClass)
    }
  }

  /** Install Entity with a default of no query access from Admin Dashboard */
  protected fun <T : DbEntity<T>> addEntity(
    dbEntityClass: KClass<T>,
    queryClass: KClass<out Query<T>>? = null,
    accessAnnotationClass: KClass<out Annotation> = NoAdminDashboardDatabaseAccess::class
  ) {
    multibind<HibernateEntity>(qualifier)
      .toInstance(HibernateEntity(dbEntityClass))

    multibind<DatabaseQueryMetadata>().toProvider(
      DatabaseQueryMetadataProvider(
        dbEntityClass = dbEntityClass,
        queryClass = queryClass,
        accessAnnotationClass = accessAnnotationClass
      )
    )
    if (queryClass != null) {
      multibind<HibernateQuery>().toInstance(
        HibernateQuery(queryClass as KClass<out Query<DbEntity<*>>>)
      )
    }
  }

  /** Install Entity with a default of no query access from Admin Dashboard */
  protected inline fun <reified T : DbEntity<T>> addEntity() {
    addEntity(T::class)
  }

  /** Adds a DbEntity to Database-Query Admin Dashboard Tab with a dynamic query
   * (not a static Misk.Query)
   */
  protected inline fun <reified T : DbEntity<T>, reified AA : Annotation>
  addEntityWithDynamicQuery() {
    addEntity(T::class, null, AA::class)
  }

  /** Adds a DbEntity to Database-Query Admin Dashboard Tab with a static Misk.Query */
  protected inline fun <reified T : DbEntity<T>, reified Q : Query<T>, reified AA : Annotation>
  addEntityWithStaticQuery() {
    addEntity(T::class, Q::class, AA::class)
  }

  protected fun <T> bindListener(
    type: EventType<T>,
    policy: BindPolicy = BindPolicy.APPEND
  ): LinkedBindingBuilder<in T> {
    // Bind the listener as an anonymous key. We can get the provider for this before its bound!
    val key = Key.get(
      Any::class.java,
      Names.named("HibernateEventListener@${nextHibernateEventListener.getAndIncrement()}")
    )

    // Create a multibinding for a ListenerRegistration that uses the above key.
    multibind<ListenerRegistration>(qualifier)
      .toInstance(ListenerRegistration(type, getProvider(key), policy))

    // Start the binding.
    return bind(key)
  }
}

/** Used to ensure listeners get unique binding keys. This must be unique across all instances. */
private val nextHibernateEventListener = AtomicInteger(1)
