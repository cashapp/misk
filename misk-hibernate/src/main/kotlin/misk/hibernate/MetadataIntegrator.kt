package misk.hibernate

import org.hibernate.boot.Metadata
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.integrator.spi.Integrator
import org.hibernate.service.spi.SessionFactoryServiceRegistry

object MetadataIntegrator : Integrator {
  lateinit var metadata: Metadata
  override fun integrate(
    metadata: Metadata,
    sessionFactory: SessionFactoryImplementor?,
    serviceRegistry: SessionFactoryServiceRegistry?
  ) {
    this.metadata = metadata
  }

  override fun disintegrate(
    sessionFactory: SessionFactoryImplementor?,
    serviceRegistry: SessionFactoryServiceRegistry?
  ) {
    // Nothing to do.
  }

}
