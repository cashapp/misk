package misk.web.metadata.webaction

import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.CustomCapabilityAccessAction
import misk.web.actions.CustomServiceAccessAction
import misk.web.actions.GrpcAction
import misk.web.mediatype.MediaTypes
import misk.web.metadata.MetadataTestingModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class WebActionMetadataActionTest {
  @MiskTestModule
  val module = MetadataTestingModule()

  @Inject lateinit var webActionMetadataAction: WebActionMetadataAction

  @Test fun `custom service access`() {
    val response = webActionMetadataAction.getAll()

    val metadata = response.webActionMetadata.find {
      it.name == CustomServiceAccessAction::class.simpleName
    }!!
    assertThat(metadata.requestMediaTypes).containsOnly("*/*")
    assertThat(metadata.responseMediaType).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(metadata.parameterTypes).isEmpty()
    assertThat(metadata.pathPattern).isEqualTo("/custom_service_access")
    assertThat(metadata.allowedServices).containsOnly("payments")
    assertThat(metadata.allowedCapabilities).isEmpty()
  }

  @Test fun `custom capability access`() {
    val response = webActionMetadataAction.getAll()

    val metadata = response.webActionMetadata.find {
      it.name == CustomCapabilityAccessAction::class.simpleName
    }!!
    assertThat(metadata.requestMediaTypes).containsOnly("*/*")
    assertThat(metadata.responseMediaType).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(metadata.parameterTypes).isEmpty()
    assertThat(metadata.pathPattern).isEqualTo("/custom_capability_access")
    assertThat(metadata.allowedServices).isEmpty()
    assertThat(metadata.allowedCapabilities).containsOnly("admin")
  }

  @Test fun `request type`() {
    val response = webActionMetadataAction.getAll()

    val metadata = response.webActionMetadata.find {
      it.name == GrpcAction::class.simpleName
    }!!
    assertThat(metadata.requestType).isEqualTo(Shipment::class.qualifiedName)
    assertThat(metadata.types).isNotEmpty
  }

  @Test fun `protobuf documentation url prefix`() {
    val response = webActionMetadataAction.getAll()

    val protobufDocUrlPrefix = response.protobufDocUrlPrefix
    assertThat(protobufDocUrlPrefix).isEqualTo("https://example.com/")
  }

  @Test fun `grpc`() {
    val response = webActionMetadataAction.getAll()

    val metadata = response.webActionMetadata.find {
      it.name == GrpcAction::class.simpleName
    }!!
    assertThat(metadata.httpMethod).isEqualTo("POST")
    assertThat(metadata.requestType).isEqualTo(Shipment::class.qualifiedName)
    assertThat(metadata.returnType).isEqualTo(Warehouse::class.qualifiedName)
    assertThat(metadata.types).isNotEmpty
  }
}
