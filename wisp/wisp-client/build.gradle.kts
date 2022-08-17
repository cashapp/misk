plugins {
    `java-library`
}

sourceSets {
    val main by getting {
        java.srcDir("src/main/kotlin/")
    }
}

dependencies {
    api(project(":wisp-ssl"))
    api(project(":wisp-logging"))
    implementation(libs.jnrUnixsocket)
    implementation(libs.okHttp)
    implementation(libs.okio)
    implementation(libs.openTracing)
    implementation(libs.openTracingOkHttp)
}
