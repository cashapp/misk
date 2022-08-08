package misk.hibernate

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import misk.jdbc.DataSourceConnector
import misk.jdbc.DataSourceType
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
import org.hibernate.mapping.Property
import org.hibernate.mapping.SimpleValue
import org.hibernate.mapping.Value
import org.hibernate.service.spi.SessionFactoryServiceRegistry
import org.hibernate.usertype.UserType
import wisp.logging.getLogger
import javax.inject.Provider
import javax.persistence.Column
import javax.persistence.Table
import javax.sql.DataSource
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.jvmName

private val logger = getLogger<SessionFactoryService>()

