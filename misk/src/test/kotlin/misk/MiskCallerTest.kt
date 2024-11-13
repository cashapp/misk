package misk

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import jakarta.inject.Qualifier

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

  @Test fun `hasCapability should be true when capabilities contains an allowedCapability`() {
    val hasCapability = testUser.hasCapability(setOf("not_testing", "testing", "other_capability"))
    assertThat(hasCapability).isTrue()
  }

  @Test fun `hasCapability should be false when capabilities does not contain an allowedCapability`() {
    val hasCapability = testUser.hasCapability(setOf("not_testing", "other_capability"))
    assertThat(hasCapability).isFalse()
  }
}

internal class MiskCallerTestModule : KAbstractModule() {
  override fun configure() {
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
