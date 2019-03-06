package misk.web.actions

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class WebActionMetadataActionTest {
  @MiskTestModule
  val module = TestAdminDashboardActionModule()

  @Inject lateinit var webActionMetadataAction: WebActionMetadataAction

  @Test fun webActionMetadata() {
    val response = webActionMetadataAction.getAll()

    val customServiceActionMetadata = response.webActionMetadata.find {
      it.name.equals(TestWebActionModule.CustomServiceAccessAction::class.simpleName)
    }
    assertThat(customServiceActionMetadata).isNotNull()
    assertThat(customServiceActionMetadata!!.requestMediaTypes).containsOnly("*/*")
    assertThat(customServiceActionMetadata.responseMediaType).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(customServiceActionMetadata.parameterTypes).isEmpty()
    assertThat(customServiceActionMetadata.pathPattern).isEqualTo("/custom_service_access")
    assertThat(customServiceActionMetadata.allowedServices).containsOnly("payments")
    assertThat(customServiceActionMetadata.allowedRoles).isEmpty()

    val customRoleActionMetadata = response.webActionMetadata.find {
      it.name.equals(TestWebActionModule.CustomRoleAccessAction::class.simpleName)
    }
    assertThat(customRoleActionMetadata!!.requestMediaTypes).containsOnly("*/*")
    assertThat(customRoleActionMetadata.responseMediaType).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(customRoleActionMetadata.parameterTypes).isEmpty()
    assertThat(customRoleActionMetadata.pathPattern).isEqualTo("/custom_role_access")
    assertThat(customRoleActionMetadata.allowedServices).isEmpty()
    assertThat(customRoleActionMetadata.allowedRoles).containsOnly("admin")
  }
}