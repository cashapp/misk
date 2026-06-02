package misk.web.metadata.guice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GitHubSourceUrlProviderTest {
  @Test
  fun testUrlForSource() {
    verifyUrl(
      "com.squareup.misk.dashboard.MiskDashboardModule.configure(MiskDashboardModule.kt:23)",
      "https://github.com/search?q=%22package+com.squareup.misk.dashboard%22+MiskDashboardModule+configure&type=code",
    )
  }

  @Test
  fun testUrlForSourceShortPackage() {
    verifyUrl(
      "package.Class.function(Class.kt:23)",
      "https://github.com/search?q=%22package+package%22+Class+function&type=code",
    )
  }

  @Test
  fun testUrlForSourceInnerClass() {
    verifyUrl(
      "package.Class\$InnerClass.function(Class.kt:23)",
      "https://github.com/search?q=%22package+package%22+Class+InnerClass+function&type=code",
    )
  }

  @Test
  fun testUrlForFunction() {
    verifyUrl(
      "public final com.squareup.wire.reflector.SchemaReflector misk.grpc.reflect.GrpcReflectModule.provideServiceReflector(com.squareup.wire.schema.Schema, com.squareup.wire.schema.Schema)",
      "https://github.com/search?q=%22package+misk.grpc.reflect%22+GrpcReflectModule+provideServiceReflector&type=code",
    )
  }

  @Test
  fun testUrlForFunctionWithDollar() {
    verifyUrl(
      "public final com.squareup.wire.reflector.SchemaReflector misk.grpc.reflect.GrpcReflectModule.provideServiceReflector\$service(com.squareup.wire.schema.Schema, com.squareup.wire.schema.Schema)",
      "https://github.com/search?q=%22package+misk.grpc.reflect%22+GrpcReflectModule+provideServiceReflector&type=code",
    )
  }

  @Test
  fun testUrlForFunctionWithNoArguments() {
    verifyUrl(
      "public final com.squareup.wire.reflector.SchemaReflector misk.grpc.reflect.GrpcReflectModule.provideServiceReflector()",
      "https://github.com/search?q=%22package+misk.grpc.reflect%22+GrpcReflectModule+provideServiceReflector&type=code",
    )
  }

  @Test
  fun testUrlForClass() {
    verifyUrl(
      "class com.squareup.skim.logging.SkimLoggingStartupCheck",
      "https://github.com/search?q=%22package+com.squareup.skim.logging%22+SkimLoggingStartupCheck&type=code",
    )
  }

  @Test
  fun testUrlForInnerClass() {
    verifyUrl(
      "class misk.metrics.MetricsModule\$V2MetricsProvider",
      "https://github.com/search?q=%22package+misk.metrics%22+MetricsModule+V2MetricsProvider&type=code",
    )
  }

  private fun verifyUrl(source: String, expectedUrl: String) {
    val provider = GitHubSourceUrlProvider()
    val url = provider.urlForSource(source)
    assertEquals(expectedUrl, url)
  }
}
