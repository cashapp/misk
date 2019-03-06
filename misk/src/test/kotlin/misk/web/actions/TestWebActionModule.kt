package misk.web.actions

import misk.MiskCaller
import misk.inject.KAbstractModule
import misk.scope.ActionScoped
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import javax.inject.Inject

// Common module for web action-related tests to use to use that bind up some sample web actions
class TestWebActionModule : KAbstractModule() {
  override fun configure() {
    install(WebTestingModule())
    install(AccessControlModule())

    install(WebActionModule.create<CustomServiceAccessAction>())
    install(WebActionModule.create<CustomRoleAccessAction>())

    multibind<AccessAnnotationEntry>().toInstance(
        AccessAnnotationEntry<CustomServiceAccess>(services = listOf("payments")))
    multibind<AccessAnnotationEntry>().toInstance(
        AccessAnnotationEntry<CustomRoleAccess>(roles = listOf("admin")))
    multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
  }

  class CustomServiceAccessAction @Inject constructor() : WebAction {
    @Inject
    lateinit var scopedCaller: ActionScoped<MiskCaller?>

    @Get("/custom_service_access")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @CustomServiceAccess
    fun get() = "${scopedCaller.get()} authorized as custom service".toResponseBody()
  }

  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION)
  annotation class CustomServiceAccess

  class CustomRoleAccessAction @Inject constructor() : WebAction {
    @Inject
    lateinit var scopedCaller: ActionScoped<MiskCaller?>

    @Get("/custom_role_access")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @CustomRoleAccess
    fun get() = "${scopedCaller.get()} authorized as custom role".toResponseBody()
  }

  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION)
  annotation class CustomRoleAccess
}
