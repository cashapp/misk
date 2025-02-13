import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile

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
  dependsOn("compileDevelopmentExecutableKotlinWasmWasi")
  // dependsOn("compileDevelopmentExecutableKotlinWasmWasiOptimize")
}

// The Kotlin Wasm compiler generates legacy opcodes for the exception proposal by default. Chasm
// supports the new exception opcodes.
tasks.withType<KotlinJsCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.addAll("-Xwasm-use-new-exception-proposal")
  }
}
