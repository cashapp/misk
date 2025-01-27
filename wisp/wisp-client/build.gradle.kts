plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

sourceSets {
    main {
        java.srcDir("src/main/kotlin/")
    }
}

dependencies {
    api(libs.okHttp)
    api(project(":wisp:wisp-ssl"))
    implementation(libs.jnrUnixsocket)
    implementation(project(":wisp:wisp-resource-loader"))

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
}
