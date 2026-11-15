package misk.web.metadata.all

import jakarta.inject.Inject
import kotlin.test.assertEquals
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataTestingModule
import misk.web.metadata.database.DatabaseQueryMetadata
import misk.web.metadata.jvm.JvmRuntime
import misk.web.metadata.webaction.WebActionMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class AllMetadataActionTest {
  @MiskTestModule val module = MetadataTestingModule()

  @Inject lateinit var action: AllMetadataAction

  // TODO fix this test to rely on Misk injected moshi to generate response and assert against JSON instead of
  // toString()
  //    so then can remove the manual fix in ServiceGraphBuilder.kt
  @Test
  fun `get all`() {
    val actual = action.getAll("all")
    val actualIds = actual.all.keys
    assertEquals(setOf("service-graph", "guice", "config", "database-hibernate", "jvm", "web-actions"), actualIds)

    // Config
    val actualConfig = actual.all["config"]!!
    assertConfig(actualConfig)

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
      """
        .trimMargin(),
      (actualServiceGraphMetadata.metadata).toString(),
    )

    // JVM
    val actualJvmMetadata = actual.all["jvm"]!!
    assertThat((actualJvmMetadata.metadata as JvmRuntime).toString()).contains("JvmRuntime")

    // Web Action Metadata
    val actualWebActionsMetadata = actual.all["web-actions"]!!
    assertEquals(
      """
      WebActionMetadata(name=ServerReflectionApi, function=fun misk.grpc.reflect.ServerReflectionApi.ServerReflectionInfo(com.squareup.wire.MessageSource<grpc.reflection.v1alpha.ServerReflectionRequest>, com.squareup.wire.MessageSink<grpc.reflection.v1alpha.ServerReflectionResponse>): kotlin.Unit, packageName=misk.grpc.reflect, description=null, functionAnnotations=[@misk.security.authz.Unauthenticated(), @com.squareup.wire.WireRpc(sourceFile="grpc/reflection/v1alpha/reflection.proto", path="/grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo", requestAdapter="grpc.reflection.v1alpha.ServerReflectionRequest#ADAPTER", responseAdapter="grpc.reflection.v1alpha.ServerReflectionResponse#ADAPTER")], requestMediaTypes=[application/grpc, application/grpc+proto], responseMediaType=application/grpc, parameterTypes=[com.squareup.wire.MessageSource<grpc.reflection.v1alpha.ServerReflectionRequest>, com.squareup.wire.MessageSink<grpc.reflection.v1alpha.ServerReflectionResponse>], parameters=[ParameterMetaData(name=requests, annotations=[], type=com.squareup.wire.MessageSource<grpc.reflection.v1alpha.ServerReflectionRequest>), ParameterMetaData(name=responses, annotations=[], type=com.squareup.wire.MessageSink<grpc.reflection.v1alpha.ServerReflectionResponse>)], requestType=null, returnType=kotlin.Unit, responseType=null, types={}, responseTypes={}, returnTypes={}, pathPattern=/grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo, applicationInterceptors=[], networkInterceptors=[misk.web.interceptors.GunzipRequestBodyInterceptor, misk.web.interceptors.InternalErrorInterceptorFactory${'$'}Companion${'$'}INTERCEPTOR${'$'}1, misk.web.interceptors.RequestLogContextInterceptor, misk.web.interceptors.MetricsInterceptor, misk.web.exceptions.ExceptionHandlingInterceptor], httpMethod=POST, allowedServices=[], allowedCapabilities=[])
      """
        .trimIndent(),
      (actualWebActionsMetadata.metadata as List<WebActionMetadata>).first().toString(),
    )
  }

  @Test
  fun `only config`() {
    val actual = action.getAll("config")
    val actualIds = actual.all.keys
    assertEquals(setOf("config"), actualIds)

    // Config
    val actualConfig = actual.all["config"]!!
    assertConfig(actualConfig)
  }

  private fun assertConfig(actualConfig: Metadata) {
    assertThat(actualConfig.metadata as Map<String, String?>).containsKey("Effective Config")
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

      """
        .trimIndent(),
      (actualConfig.metadata as Map<String, String?>).get("Effective Config"),
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

      """
        .trimIndent(),
      (actualConfig.metadata as Map<String, String?>).get("classpath:/admin-dashboard-app-common.yaml"),
    )
    assertEquals(
      """
      included:
        key: testing

      redacted:
        key: abc123
      """
        .trimIndent(),
      (actualConfig.metadata as Map<String, String?>).get("classpath:/admin-dashboard-app-testing.yaml"),
    )
  }
}
