package misk.environment

import misk.inject.KAbstractModule

class EnvironmentModule(val environment: Environment) : KAbstractModule() {
    override fun configure() {
        bind<Environment>().toInstance(environment)
    }

}
