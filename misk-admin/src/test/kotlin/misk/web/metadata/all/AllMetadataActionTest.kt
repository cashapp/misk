package misk.web.metadata.all

import jakarta.inject.Inject
import misk.ServiceGraphBuilderMetadata
import misk.metadata.servicegraph.ServiceGraphMetadata
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.metadata.MetadataTestingModule
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
    val actualIds = actual.all.keys
    assertEquals(setOf("service-graph", "config", "database-hibernate", "web-actions"), actualIds)

    // Config
    val actualConfig = actual.all["config"]!!
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
    val actualDatabaseHibernate = actual.all["database-hibernate"]!!
    assertEquals(listOf(), (actualDatabaseHibernate.metadata as List<DatabaseQueryMetadata>))

    // Service Graph
    val actualServiceGraphMetadata = actual.all["service-graph"]!!
    assertEquals(
      """
        |ServiceGraphBuilderMetadata(serviceMap={Key[type=misk.web.jetty.JettyService, annotation=[none]]=CoordinatedServiceMetadata(dependencies=[], directDependsOn=[misk.ReadyService]), Key[type=misk.web.jetty.JettyThreadPoolMetricsCollector, annotation=[none]]=CoordinatedServiceMetadata(dependencies=[misk.ReadyService], directDependsOn=[]), Key[type=misk.web.jetty.JettyConnectionMetricsCollector, annotation=[none]]=CoordinatedServiceMetadata(dependencies=[misk.ReadyService], directDependsOn=[]), Key[type=misk.web.actions.ReadinessCheckService, annotation=[none]]=CoordinatedServiceMetadata(dependencies=[misk.ReadyService], directDependsOn=[]), Key[type=misk.tasks.RepeatedTaskQueue, annotation=@misk.web.ReadinessRefreshQueue]=CoordinatedServiceMetadata(dependencies=[], directDependsOn=[]), Key[type=misk.ReadyService, annotation=[none]]=CoordinatedServiceMetadata(dependencies=[misk.web.jetty.JettyService], directDependsOn=[misk.web.jetty.JettyThreadPoolMetricsCollector, misk.web.jetty.JettyConnectionMetricsCollector, misk.web.actions.ReadinessCheckService])}, serviceNames={Key[type=misk.web.jetty.JettyService, annotation=[none]]=misk.web.jetty.JettyService, Key[type=misk.web.jetty.JettyThreadPoolMetricsCollector, annotation=[none]]=misk.web.jetty.JettyThreadPoolMetricsCollector, Key[type=misk.web.jetty.JettyConnectionMetricsCollector, annotation=[none]]=misk.web.jetty.JettyConnectionMetricsCollector, Key[type=misk.web.actions.ReadinessCheckService, annotation=[none]]=misk.web.actions.ReadinessCheckService, Key[type=misk.tasks.RepeatedTaskQueue, annotation=@misk.web.ReadinessRefreshQueue]=misk.tasks.RepeatedTaskQueue, Key[type=misk.ReadyService, annotation=[none]]=misk.ReadyService}, dependencyMap={Key[type=misk.ReadyService, annotation=[none]]=[Key[type=misk.web.jetty.JettyService, annotation=[none]]], Key[type=misk.web.jetty.JettyThreadPoolMetricsCollector, annotation=[none]]=[Key[type=misk.ReadyService, annotation=[none]]], Key[type=misk.web.jetty.JettyConnectionMetricsCollector, annotation=[none]]=[Key[type=misk.ReadyService, annotation=[none]]], Key[type=misk.web.actions.ReadinessCheckService, annotation=[none]]=[Key[type=misk.ReadyService, annotation=[none]]]}, asciiVisual=misk.web.jetty.JettyService
        |    \__ misk.ReadyService
        |        |__ misk.web.jetty.JettyThreadPoolMetricsCollector
        |        |__ misk.web.jetty.JettyConnectionMetricsCollector
        |        \__ misk.web.actions.ReadinessCheckService
        |misk.tasks.RepeatedTaskQueue
        |)
      """.trimMargin(),
      (actualServiceGraphMetadata.metadata as ServiceGraphBuilderMetadata).toString()
    )

    // Web Action Metadata
    val actualWebActionsMetadata = actual.all["web-actions"]!!
    assertEquals(
      """
        WebActionMetadata(name=StatusAction, function=fun misk.web.actions.StatusAction.getStatus(): misk.web.actions.StatusAction.ServerStatus, packageName=misk.web.actions, description=null, functionAnnotations=[@misk.web.Get(pathPattern="/_status"), @misk.web.ResponseContentType({"application/json;charset=utf-8"}), @misk.security.authz.Unauthenticated(), @misk.web.AvailableWhenDegraded()], requestMediaTypes=[*/*], responseMediaType=application/json;charset=utf-8, parameterTypes=[], parameters=[], requestType=null, returnType=misk.web.actions.StatusAction.ServerStatus, responseType=null, types={}, responseTypes={}, pathPattern=/_status, applicationInterceptors=[], networkInterceptors=[misk.web.interceptors.GunzipRequestBodyInterceptor, misk.web.interceptors.InternalErrorInterceptorFactory${'$'}Companion${'$'}INTERCEPTOR${'$'}1, misk.web.interceptors.RequestLogContextInterceptor, misk.web.interceptors.MetricsInterceptor, misk.web.exceptions.ExceptionHandlingInterceptor], httpMethod=GET, allowedServices=[], allowedCapabilities=[])
      """.trimIndent(),
      (actualWebActionsMetadata.metadata as List<WebActionMetadata>).first().toString()
    )

  }
}
