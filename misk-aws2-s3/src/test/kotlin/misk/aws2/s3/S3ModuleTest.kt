package misk.aws2.s3

import com.google.inject.util.Modules
import jakarta.inject.Inject
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import misk.MiskTestingServiceModule
import misk.cloud.aws.AwsEnvironmentModule
import misk.cloud.aws.FakeAwsEnvironmentModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client

@MiskTest(startService = false)
class S3ModuleTest {
  @MiskTestModule
  val module =
    Modules.combine(
      MiskTestingServiceModule(),
      MockTracingBackendModule(),
      AwsEnvironmentModule(),
      FakeAwsEnvironmentModule(),
      S3TestModule(),
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
