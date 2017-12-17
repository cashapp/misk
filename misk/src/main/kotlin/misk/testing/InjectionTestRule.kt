package misk.testing

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import misk.inject.uninject
import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

open class InjectionTestRule(vararg val modules: Module) : MethodRule {
    override fun apply(base: Statement, method: FrameworkMethod, target: Any): Statement {
        return object : Statement() {
            override fun evaluate() {
                val injector = Guice.createInjector(modules.asList())
                injector.injectMembers(target)
                try {
                    beforeMethod(injector, method, target)
                    base.evaluate()
                } finally {
                    afterMethod(injector, method, target)
                    uninject(target)
                }
            }
        }
    }

    open fun beforeMethod(injector: Injector, method: FrameworkMethod, target: Any) {}
    open fun afterMethod(injector: Injector, method: FrameworkMethod, target: Any) {}

}
