pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("com.gradle.develocity") version "3.17.5"
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
  group = when {
    path.startsWith(":wisp") -> "app.cash.wisp"
    else -> "com.squareup.misk"
  }
  version = findProperty("VERSION_NAME") as? String ?: "0.0-SNAPSHOT"
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
include(":misk-api")
include(":misk-aws")
include(":misk-aws-dynamodb")
include(":misk-aws2-dynamodb")
include(":misk-bom")
include(":misk-clustering")
include(":misk-clustering-dynamodb")
include(":misk-config")
include(":misk-core")
include(":misk-cron")
include(":misk-crypto")
include(":misk-datadog")
include(":misk-events")
include(":misk-events-core")
include(":misk-events-testing")
include(":misk-exceptions-dynamodb")
include(":misk-feature")
include(":misk-feature-testing")
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
include(":misk-rate-limiting-bucket4j-mysql")
include(":misk-rate-limiting-bucket4j-redis")
include(":misk-redis")
include(":misk-schema-migrator-gradle-plugin")
include(":misk-service")
include(":misk-slack")
include(":misk-sqldelight")
include(":misk-sqldelight-testing")
include(":misk-tailwind")
include(":misk-testing")
include(":misk-transactional-jobqueue")
include(":misk-warmup")
include(":samples:exemplar")
include(":samples:exemplarchat")

