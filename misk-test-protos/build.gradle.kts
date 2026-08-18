plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.squareup.wire")
}

dependencies {
  api(libs.okio)
  api(libs.wireGrpcClient)
}

wire {
  java {
    exclusive = false
  }

  kotlin {
    rpcRole = "client"
    rpcCallStyle = "blocking"
    exclusive = false
    includes = listOf(
      "helloworld.Greeter"
    )
  }

  kotlin {
    rpcRole = "server"
    rpcCallStyle = "blocking"
    exclusive = false
    singleMethodServices = true
    includes = listOf(
      "helloworld.Greeter"
    )
  }
}
