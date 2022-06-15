plugins {
    kotlin("jvm")
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
    implementation(Dependencies.jnrUnixsocket)
    implementation(Dependencies.okHttp)
    implementation(Dependencies.okio)
    implementation(Dependencies.openTracing)
    implementation(Dependencies.openTracingOkHttp)
}
