package misk.web

import misk.MiskCaller
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.scope.ActionScoped
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class MiskCallerExtensionTest {
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(WebServerTestingModule())
    }
  }

  @Inject lateinit var miskCaller: ActionScoped<MiskCaller>

  open inner class AssertOnActionScoped(private val expectedCaller: MiskCaller) {
    @Test
    fun `injects an ActionScoped`() {
      assertThat(miskCaller.get()).isEqualTo(expectedCaller)
    }
  }

  @Nested
  @WithMiskCaller
  inner class Default : AssertOnActionScoped(MiskCaller(user = "default-user"))

  @Nested
  @WithMiskCaller(user = "user")
  inner class WithUser : AssertOnActionScoped(MiskCaller(user = "user"))

  @Nested
  @WithMiskCaller(service = "service")
  inner class WithService : AssertOnActionScoped(MiskCaller(service = "service"))

}
