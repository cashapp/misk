plugins {
  id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
  jvm {
  }

  wasmWasi {
    nodejs()
    binaries.executable()
  }

  sourceSets {
    val jvmTest by getting {
      dependencies {
        implementation("io.github.charlietap.chasm:chasm:0.9.52")
        implementation(libs.assertj)
        implementation(libs.junitApi)
        implementation(libs.junitEngine)
        implementation(libs.okio)
      }
    }
  }
}

tasks.named("jvmTest") {
  dependsOn("compileKotlinWasmWasi")
}
