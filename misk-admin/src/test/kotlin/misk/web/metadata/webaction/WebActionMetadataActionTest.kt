package misk.web.metadata.webaction

import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.MiskWebFormBuilder
import misk.web.actions.CustomCapabilityAccessAction
import misk.web.actions.CustomServiceAccessAction
import misk.web.actions.DataClassEntry
import misk.web.actions.DataClassRequest
import misk.web.actions.DataClassRequestAction
import misk.web.actions.GrpcAction
import misk.web.mediatype.MediaTypes
import misk.web.metadata.MetadataTestingModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class WebActionMetadataActionTest {
  @MiskTestModule val module = MetadataTestingModule()

  @Inject lateinit var webActionMetadataAction: WebActionMetadataAction

  @Test
  fun `custom service access`() {
    val response = webActionMetadataAction.getAll()

    val metadata = response.webActionMetadata.find { it.name == CustomServiceAccessAction::class.simpleName }!!
    assertThat(metadata.requestMediaTypes).containsOnly("*/*")
    assertThat(metadata.responseMediaType).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(metadata.parameterTypes).isEmpty()
    assertThat(metadata.pathPattern).isEqualTo("/custom_service_access")
    assertThat(metadata.allowedServices).containsOnly("payments")
    assertThat(metadata.allowedCapabilities).isEmpty()
  }

  @Test
  fun `custom capability access`() {
    val response = webActionMetadataAction.getAll()

    val metadata = response.webActionMetadata.find { it.name == CustomCapabilityAccessAction::class.simpleName }!!
    assertThat(metadata.requestMediaTypes).containsOnly("*/*")
    assertThat(metadata.responseMediaType).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(metadata.parameterTypes).isEmpty()
    assertThat(metadata.pathPattern).isEqualTo("/custom_capability_access")
    assertThat(metadata.allowedServices).isEmpty()
    assertThat(metadata.allowedCapabilities).containsOnly("admin")
  }

  @Test
  fun `request type`() {
    val response = webActionMetadataAction.getAll()

    val metadata = response.webActionMetadata.find { it.name == GrpcAction::class.simpleName }!!
    assertThat(metadata.requestType).isEqualTo(Shipment::class.qualifiedName)
    assertThat(metadata.types).isNotEmpty
  }

  @Test
  fun `grpc`() {
    val response = webActionMetadataAction.getAll()

    val metadata = response.webActionMetadata.find { it.name == GrpcAction::class.simpleName }!!
    assertThat(metadata.httpMethod).isEqualTo("POST")
    assertThat(metadata.requestType).isEqualTo(Shipment::class.qualifiedName)
    assertThat(metadata.returnType).isEqualTo(Warehouse::class.qualifiedName)
    assertThat(metadata.types).isNotEmpty
  }

  @Test
  fun `data class request body is introspected for form metadata`() {
    val response = webActionMetadataAction.getAll()
    val metadata = response.webActionMetadata.find { it.name == DataClassRequestAction::class.simpleName }!!

    val requestTypeKey = DataClassRequest::class.qualifiedName!!
    val entryTypeKey = DataClassEntry::class.qualifiedName!!

    assertThat(metadata.types).containsKeys(requestTypeKey, entryTypeKey)

    val requestType = metadata.types[requestTypeKey]!!
    assertThat(requestType.fields)
      .containsExactlyInAnyOrder(
        MiskWebFormBuilder.Field("token", "String", repeated = false, annotations = emptyList()),
        MiskWebFormBuilder.Field("count", "Int", repeated = false, annotations = emptyList()),
        MiskWebFormBuilder.Field("entries", entryTypeKey, repeated = true, annotations = emptyList()),
      )

    val entryType = metadata.types[entryTypeKey]!!
    assertThat(entryType.fields)
      .containsExactlyInAnyOrder(
        MiskWebFormBuilder.Field("id", "Long", repeated = false, annotations = emptyList()),
        MiskWebFormBuilder.Field("note", "String", repeated = false, annotations = emptyList()),
      )
  }
}
