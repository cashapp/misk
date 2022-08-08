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

/** Used to ensure listeners get unique binding keys. This must be unique across all instances. */
private val nextHibernateEventListener = AtomicInteger(1)
