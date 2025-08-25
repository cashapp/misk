package misk.aws2.s3

import com.google.inject.util.Modules
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.aws2.s3.config.S3Config
import misk.cloud.aws.AwsEnvironmentModule
import misk.cloud.aws.FakeAwsEnvironmentModule
import misk.inject.KAbstractModule
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@MiskTest(startService = false)
class S3ModuleTest {
  @MiskTestModule
  val module = Modules.combine(
    MiskTestingServiceModule(),
    MockTracingBackendModule(),
    AwsEnvironmentModule(),
    FakeAwsEnvironmentModule(),
    S3TestModule()
  )

  @Inject private lateinit var s3Client: S3Client
  @Inject private lateinit var s3AsyncClient: S3AsyncClient

  @Test
  fun `S3Module provides S3Client`() {
    assertNotNull(s3Client)
    assertTrue(s3Client.javaClass.simpleName.contains("S3"))
  }

  @Test
  fun `S3Module provides S3AsyncClient`() {
    assertNotNull(s3AsyncClient)
    assertTrue(s3AsyncClient.javaClass.simpleName.contains("S3"))
  }

  @Test
  fun `S3Client and S3AsyncClient are different instances`() {
    // Verify they are different objects (not the same instance)
    assertTrue(s3Client !== s3AsyncClient)
  }
}
