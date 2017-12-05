package misk.testing

import com.google.inject.Guice
import com.google.inject.Module
import misk.inject.uninject
import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

class MiskTestRule(vararg val modules: Module) : MethodRule {
    override fun apply(base: Statement, method: FrameworkMethod, target: Any): Statement {
        return object : Statement() {
            override fun evaluate() {
                val injector = Guice.createInjector(modules.asList())
                injector.injectMembers(target)
                try {
                    base.evaluate()
                } finally {
                    uninject(target)
                }
            }
        }
    }
}
