package misk.hibernate

import jakarta.inject.Qualifier
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import wisp.config.Config
import wisp.deployment.TESTING

class PrimitivesDbTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(DeploymentModule(TESTING))

    val config = MiskConfig.load<RootConfig>("primitivecolumns", TESTING)
    install(HibernateTestingModule(PrimitivesDb::class))
    install(HibernateModule(PrimitivesDb::class, config.data_source))
    install(object : HibernateEntityModule(PrimitivesDb::class) {
      override fun configureHibernate() {
        addEntities(DbPrimitiveTour::class)
      }
    })
  }
}

data class RootConfig(val data_source: DataSourceConfig) : Config


@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class PrimitivesDb

