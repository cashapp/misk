package misk.environment

import com.google.inject.AbstractModule

class EnvironmentModule(private val environment: Environment) : AbstractModule() {
    override fun configure() {
        bind(Environment::class.java).toInstance(environment)
    }

}
