package misk.web.metadata.all

import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.dashboard.AdminDashboardModule
import misk.web.metadata.MetadataTestingModule
import misk.web.metadata.config.ConfigMetadataAction
import misk.web.metadata.database.DatabaseQueryMetadata
import misk.web.metadata.webaction.WebActionMetadata
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@MiskTest(startService = true)
class AllMetadataActionTest {
  @MiskTestModule
  val module = MetadataTestingModule()

  @Inject lateinit var action: AllMetadataAction

  @Test
  fun `happy path`() {
    val actual = action.getAll()
    val actualIds = actual.all.map { it.id }
    assertEquals(listOf("config", "database-hibernate", "web-actions"), actualIds)

    // Config
    val actualConfig = actual.all.single { it.id == "config" }
    assert((actualConfig.metadata as Map<String, String?>).contains("JVM"))
    assert((actualConfig.metadata as Map<String, String?>).contains("Effective Config"))
    assertEquals(
      """
        ---
        included:
          key: "foo"
        overridden:
          key: "bar"
        password:
          password: "████████"
          passphrase: "████████"
          custom: "████████"
        secret:
          secret_key: "reference -> ████████"
        redacted: "████████"
        
      """.trimIndent(),
      (actualConfig.metadata as Map<String, String?>).get("Effective Config")
    )
    // Raw files or redaction is controlled by ConfigMetadataAction.ConfigTabMode.
    // For this test, UNSAFE_LEAK_MISK_SECRETS is set.
    //
    //    AdminDashboardModule(
    //      isDevelopment = true,
    //      configTabMode = ConfigMetadataAction.ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS,
    //    )
    assertEquals(
      """
        included:
          key: common

        overridden:
          key: ignored

        password:
          password: abc123
          passphrase: abc123
          custom: abc123

        secret:
          secret_key: "classpath:/secrets/api_key.txt"

        redacted:
          key: common123
      
      """.trimIndent(),
      (actualConfig.metadata as Map<String, String?>).get("classpath:/admin-dashboard-app-common.yaml")
    )
    assertEquals(
      """
        included:
          key: testing
        
        redacted:
          key: abc123
      """.trimIndent(),
      (actualConfig.metadata as Map<String, String?>).get("classpath:/admin-dashboard-app-testing.yaml")
    )

    // Database Hibernate Metadata
    // TODO maybe add a DB to the test so that assertions can confirm Database Hibernate metadata is included
    val actualDatabaseHibernate = actual.all.single { it.id == "database-hibernate" }
    assertEquals(listOf(), (actualDatabaseHibernate.metadata as List<DatabaseQueryMetadata>))

    // Web Action Metadata
    val actualWebActionsMetadata = actual.all.single { it.id == "web-actions" }
    assertEquals(
      """
        WebActionMetadata(name=StatusAction, function=fun misk.web.actions.StatusAction.getStatus(): misk.web.actions.StatusAction.ServerStatus, packageName=misk.web.actions, description=null, functionAnnotations=[@misk.web.Get(pathPattern="/_status"), @misk.web.ResponseContentType({"application/json;charset=utf-8"}), @misk.security.authz.Unauthenticated(), @misk.web.AvailableWhenDegraded()], requestMediaTypes=[*/*], responseMediaType=application/json;charset=utf-8, parameterTypes=[], parameters=[], requestType=null, returnType=misk.web.actions.StatusAction.ServerStatus, responseType=null, types={}, responseTypes={}, pathPattern=/_status, applicationInterceptors=[], networkInterceptors=[misk.web.interceptors.GunzipRequestBodyInterceptor, misk.web.interceptors.InternalErrorInterceptorFactory${'$'}Companion${'$'}INTERCEPTOR${'$'}1, misk.web.interceptors.RequestLogContextInterceptor, misk.web.interceptors.MetricsInterceptor, misk.web.exceptions.ExceptionHandlingInterceptor], httpMethod=GET, allowedServices=[], allowedCapabilities=[])
      """.trimIndent(),
      (actualWebActionsMetadata.metadata as List<WebActionMetadata>).first().toString()
    )

  }
}
