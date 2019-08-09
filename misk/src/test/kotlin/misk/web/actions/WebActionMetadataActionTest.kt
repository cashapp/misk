package misk.web.actions

import com.squareup.protos.test.parsing.Shipment
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class WebActionMetadataActionTest {
  @MiskTestModule
  val module = AdminDashboardActionTestingModule()

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
    assertThat(customServiceActionMetadata.allowedCapabilities).isEmpty()

    val customCapabilityActionMetadata = response.webActionMetadata.find {
      it.name.equals(TestWebActionModule.CustomCapabilityAccessAction::class.simpleName)
    }
    assertThat(customCapabilityActionMetadata!!.requestMediaTypes).containsOnly("*/*")
    assertThat(customCapabilityActionMetadata.responseMediaType).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(customCapabilityActionMetadata.parameterTypes).isEmpty()
    assertThat(customCapabilityActionMetadata.pathPattern).isEqualTo("/custom_capability_access")
    assertThat(customCapabilityActionMetadata.allowedServices).isEmpty()
    assertThat(customCapabilityActionMetadata.allowedCapabilities).containsOnly("admin")

    val requestTypeAction = response.webActionMetadata.find {
      it.name.equals(TestWebActionModule.RequestTypeAction::class.simpleName)
    }
    assertThat(requestTypeAction!!.requestType).isEqualTo(Shipment::class.qualifiedName)
    assertThat(requestTypeAction.types).isNotEmpty
  }
}
