package misk.jooq.config

import jakarta.inject.Qualifier
import misk.hibernate.HibernateModule
import misk.inject.KAbstractModule

class MultipleJdbcInstallationModule : KAbstractModule() {
  override fun configure() {
    install(ClientJooqTestingModule())
  }

  internal class HibernateTestingModule : KAbstractModule() {
    override fun configure() {
      install(HibernateModule(HibernateDbIdentifier::class, ClientJooqTestingModule.datasourceConfig.writer))
    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
  annotation class HibernateDbIdentifier
}
