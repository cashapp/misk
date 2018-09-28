package misk.hibernate

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.inject.toKey
import misk.logging.getLogger
import okio.ByteString
import org.hibernate.SessionFactory
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.boot.spi.MetadataImplementor
import org.hibernate.cfg.AvailableSettings
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.integrator.spi.Integrator
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.SimpleValue
import org.hibernate.mapping.Value
import org.hibernate.service.spi.SessionFactoryServiceRegistry
import org.hibernate.usertype.UserType
import javax.inject.Provider
import javax.inject.Singleton
import javax.sql.DataSource
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

private val logger = getLogger<SessionFactoryService>()

/**
 * Builds a bare connection to a Hibernate database. Doesn't do any schema migration or validation.
 */
@Singleton
internal class SessionFactoryService(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig,
  private val dataSource: Provider<DataSource>,
  private val entityClasses: Set<HibernateEntity> = setOf(),
  private val listenerRegistrations: Set<ListenerRegistration> = setOf()
) : AbstractIdleService(), DependentService, Provider<SessionFactory> {
  private var sessionFactory: SessionFactory? = null

  override val consumedKeys = setOf<Key<*>>(DataSourceService::class.toKey(qualifier))
  override val producedKeys = setOf<Key<*>>(SessionFactoryService::class.toKey(qualifier))

  override fun startUp() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Starting @${qualifier.simpleName} Hibernate")

    require(sessionFactory == null)

    // Register event listeners.
    val integrator = object : Integrator {
      override fun integrate(
        metadata: Metadata,
        sessionFactory: SessionFactoryImplementor,
        serviceRegistry: SessionFactoryServiceRegistry
      ) {
        val eventListenerRegistry = serviceRegistry.getService(EventListenerRegistry::class.java)
        val aggregateListener = AggregateListener(listenerRegistrations)
        aggregateListener.registerAll(eventListenerRegistry)
      }

      override fun disintegrate(
        sessionFactory: SessionFactoryImplementor,
        serviceRegistry: SessionFactoryServiceRegistry
      ) {
      }
    }

    val bootstrapRegistryBuilder = BootstrapServiceRegistryBuilder()
        .applyIntegrator(integrator)
        .build()

    val registryBuilder = StandardServiceRegistryBuilder(bootstrapRegistryBuilder)
    registryBuilder.run {
      applySetting(AvailableSettings.DATASOURCE, dataSource.get())
      applySetting(AvailableSettings.DIALECT, config.type.hibernateDialect)
      applySetting(AvailableSettings.SHOW_SQL, "false")
      applySetting(AvailableSettings.USE_SQL_COMMENTS, "true")
      applySetting(AvailableSettings.USE_GET_GENERATED_KEYS, "true")
      applySetting(AvailableSettings.USE_NEW_ID_GENERATOR_MAPPINGS, "false")
    }

    val registry = registryBuilder.build()

    // Register entity types.
    val metadataSources = MetadataSources(registry)
    for (entityClass in entityClasses) {
      metadataSources.addAnnotatedClass(entityClass.entity.java)
    }

    // NB: MetadataSources.getMetadataBuilder() returns a different instance each call!
    val metadataBuilder = metadataSources.metadataBuilder
    val metadata = metadataBuilder.build() as MetadataImplementor

    // Register custom type adapters so we can have columns for ByteString, Id, etc. This needs to
    // happen after we know what all of the property classes are, but before Hibernate validates
    // that their adapters exist.
    val allPropertyTypes = metadata.allSimpleValuePropertyKClasses()
    for (propertyType in allPropertyTypes) {
      val userType = findUserType(propertyType)
      if (userType != null) {
        @Suppress("DEPRECATION") // TypeResolver's replacement isn't yet specified.
        metadata.typeConfiguration.typeResolver.registerTypeOverride(
            userType, arrayOf(propertyType.jvmName))
      }
    }

    sessionFactory = metadata.buildSessionFactory()

    logger.info("Started @${qualifier.simpleName} Hibernate in $stopwatch")
  }

  /** Returns all simple value properties of all entities in this metadata. */
  private fun Metadata.allSimpleValuePropertyKClasses(): Set<KClass<*>> {
    val result = mutableSetOf<KClass<*>>()
    for (entityBinding in entityBindings) {
      for (property in entityBinding.allProperties()) {
        val value = property.value
        if (value is Component) {
          for (subProperty in value.propertyIterator) {
            if (subProperty is Property) {
              maybeAddSimpleValueProperty(subProperty.value, subProperty, entityBinding, result)
            }
          }
        } else {
          maybeAddSimpleValueProperty(value, property, entityBinding, result)
        }
      }
    }
    return result
  }

  fun maybeAddSimpleValueProperty(
    value: Value?,
    property: Property,
    entityBinding: PersistentClass,
    result: MutableSet<KClass<*>>
  ) {
    if (value is SimpleValue) {
      checkNotNull(value.typeName)
      { "property ${property.name} in class ${entityBinding.className} has null type" }

      result.add(kClassForName(value.typeName))
    }
  }

  /** Returns all properties (IDs, joined columns, regular columns) of this persistent class. */
  private fun PersistentClass.allProperties(): List<Property> {
    val result = mutableListOf<Property>()

    identifierProperty?.let {
      result.add(it)
    }

    @Suppress("UNCHECKED_CAST") // This Hibernate method returns raw types!
    val i = propertyIterator as MutableIterator<Property>
    while (i.hasNext()) {
      result.add(i.next())
    }

    return result
  }

  /** Returns a custom user type for `propertyType`, or null if the user type should be built-in. */
  private fun findUserType(propertyType: KClass<*>): UserType? {
    return when (propertyType) {
      Id::class -> IdType
      ByteString::class -> ByteStringType
      else -> BoxedStringType.create(propertyType)
    }
  }

  private fun kClassForName(name: String): KClass<out Any> {
    return when (name) {
      "boolean" -> Boolean::class
      "byte" -> Byte::class
      "short" -> Short::class
      "int" -> Int::class
      "long" -> Long::class
      "char" -> Char::class
      "float" -> Float::class
      "double" -> Double::class
      else -> Class.forName(name).kotlin
    }
  }

  override fun shutDown() {
    val stopwatch = Stopwatch.createStarted()
    logger.info("Stopping @${qualifier.simpleName} Hibernate")

    require(sessionFactory != null)
    sessionFactory!!.close()

    logger.info("Stopped @${qualifier.simpleName} Hibernate in $stopwatch")
  }

  override fun get(): SessionFactory {
    return sessionFactory ?: throw IllegalStateException(
        "@${qualifier.simpleName} Hibernate not connected: did you forget to start the service?")
  }
}
