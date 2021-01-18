package misk

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Qualifier

@MiskTest(startService = false)
internal class MiskCallerTest {
  @MiskTestModule val module = MiskCallerTestModule()
  @Inject @TestUser lateinit var testUser: MiskCaller
  @Inject @TestService lateinit var testService: MiskCaller

  @Test fun userNameIsRedactedFromToString() {
    assertThat("$testUser").doesNotContain(testUser.user)
  }
  @Test fun serviceNameIsNotRedactedFromToString() {
    assertThat("$testService").contains("${testService.service}")
  }
}

internal class MiskCallerTestModule : KAbstractModule() {
  override fun configure() {
    install(ServiceManagerModule())
    bind<MiskCaller>().annotatedWith<TestUser>()
        .toInstance(MiskCaller(user = "Test user", capabilities = setOf("testing")))
    bind<MiskCaller>().annotatedWith<TestService>()
        .toInstance(MiskCaller(service = "Test service"))
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class TestUser

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class TestService