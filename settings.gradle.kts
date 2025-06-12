import org.gradle.plugins.ide.idea.model.IdeaModel

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  // When updating the cash plugin versions, update .buildkite/scripts/copy.bara.sky too
  id("com.squareup.cash.develocity") version "1.267.1"
  id("com.squareup.cash.remotecache") version "1.267.1"
  id("com.gradle.develocity") version "4.0.2"
}

develocity {
  buildScan {
    publishing {
      termsOfUseUrl = "https://gradle.com/terms-of-service"
      termsOfUseAgree = "yes"
    }
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    maven(url = "https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
  }
}

gradle.lifecycle.beforeProject {
  val g = when {
    // The root "misk" project isn't a code-containing project (it's not a module). It doesn't need
    // a group, and in fact giving it a group confuses gradle when we `includeBuild("misk")`
    // elsewhere, because doing that makes `misk/` and `misk/misk/` _identical_ in GA
    // (group-artifact) terms.
    path == ":" -> null
    path.startsWith(":wisp") -> "app.cash.wisp"
    else -> "com.squareup.misk"
  }

  // In the case of the root project, we let it be the default value
  if (g != null) {
    group = g
  }

  version = findProperty("VERSION_NAME") as? String ?: "0.0-SNAPSHOT"

  apply(plugin = "idea")

  configure<IdeaModel> {
    module.isDownloadSources = false
    module.isDownloadJavadoc = false
  }
}

include(":detektive")
include(":wisp:wisp-aws-environment")
include(":wisp:wisp-client")
include(":wisp:wisp-config")
include(":wisp:wisp-containers-testing")
include(":wisp:wisp-deployment")
include(":wisp:wisp-deployment-testing")
include(":wisp:wisp-feature")
include(":wisp:wisp-feature-testing")
include(":wisp:wisp-launchdarkly")
include(":wisp:wisp-lease")
include(":wisp:wisp-lease-testing")
include(":wisp:wisp-logging")
include(":wisp:wisp-logging-testing")
include(":wisp:wisp-moshi")
include(":wisp:wisp-rate-limiting")
include(":wisp:wisp-rate-limiting:bucket4j")
include(":wisp:wisp-resource-loader")
include(":wisp:wisp-resource-loader-testing")
include(":wisp:wisp-sampling")
include(":wisp:wisp-ssl")
include(":wisp:wisp-task")
include(":wisp:wisp-time-testing")
include(":wisp:wisp-token")
include(":wisp:wisp-token-testing")
include(":wisp:wisp-tracing")
include(":misk")
include(":misk-action-scopes")
include(":misk-actions")
include(":misk-admin")
include(":misk-admin-web-actions")
include(":misk-api")
include(":misk-audit-client")
include(":misk-aws")
include(":misk-aws-dynamodb")
include(":misk-aws2-dynamodb")
include(":misk-aws2-sqs")
include(":misk-backoff")
include(":misk-bom")
include(":misk-clustering")
include(":misk-clustering-dynamodb")
include(":misk-config")
include(":misk-core")
include(":misk-cron")
include(":misk-crypto")
include(":misk-datadog")
include(":misk-docker")
include(":misk-events")
include(":misk-events-core")
include(":misk-exceptions-dynamodb")
include(":misk-feature")
include(":misk-gcp")
include(":misk-grpc-reflect")
include(":misk-grpc-tests")
include(":misk-hibernate")
include(":misk-hibernate-testing")
include(":misk-hotwire")
include(":misk-inject")
include(":misk-inject:misk-inject-guice7-test")
include(":misk-jdbc")
include(":misk-jobqueue")
include(":misk-jooq")
include(":misk-launchdarkly")
include(":misk-launchdarkly-core")
include(":misk-lease")
include(":misk-metrics")
include(":misk-metrics-digester")
include(":misk-policy")
include(":misk-prometheus")
include(":misk-proto")
include(":misk-rate-limiting-bucket4j-dynamodb-v1")
include(":misk-rate-limiting-bucket4j-dynamodb-v2")
include(":misk-rate-limiting-bucket4j-mysql")
include(":misk-rate-limiting-bucket4j-redis")
include(":misk-redis")
include(":misk-redis-lettuce")
include(":misk-schema-migrator-gradle-plugin")
include(":misk-service")
include(":misk-slack")
include(":misk-sqldelight")
include(":misk-sqldelight-testing")
include(":misk-tailwind")
include(":misk-testing")
include(":misk-testing-api")
include(":misk-transactional-jobqueue")
include(":misk-warmup")
include(":misk-vitess")
include(":misk-vitess-database-gradle-plugin")
include(":samples:exemplar")
include(":samples:exemplarchat")
