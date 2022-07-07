package misk.web

import com.google.inject.Injector
import misk.MiskCaller
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.testing.retrieve
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

class MiskCallerExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback {
  override fun beforeTestExecution(context: ExtensionContext) {
    val injector = context.retrieve<Injector>("injector")
    val actionScopeProvider = injector.getBinding(ActionScope::class.java)
    val actionScope = actionScopeProvider.provider.get()
    actionScope.enter(
      mapOf(
        keyOf<MiskCaller>() to context.getPrincipal()
      )
    )
  }

  override fun afterTestExecution(context: ExtensionContext) {
    val injector = context.retrieve<Injector>("injector")
    val actionScope = injector.getBinding(ActionScope::class.java)
    actionScope.provider.get().close()
  }

  private fun ExtensionContext.getPrincipal(): MiskCaller {
    val annotation =
      requiredTestInstances.allInstances.last().javaClass
        .getAnnotationsByType(WithMiskCaller::class.java)[0]
    return when {
      annotation.user.isNotBlank() -> MiskCaller(user = annotation.user)
      annotation.service.isNotBlank() -> MiskCaller(service = annotation.service)
      else -> MiskCaller(user = "default-user")
    }
  }
}

/**
 * Use this annotation to specify an ActionScoped<MiskCaller> for this class.
 *
 * Annotate after [misk.testing.MiskTest].
 */
@Target(AnnotationTarget.CLASS)
@ExtendWith(MiskCallerExtension::class)
annotation class WithMiskCaller(val user: String = "", val service: String = "")

