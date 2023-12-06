plugins {
    `java-library`
}

sourceSets {
    val main by getting {
        java.srcDir("src/main/kotlin/")
    }
}

dependencies {
    api(Dependencies.okHttp)
    api(project(":wisp:wisp-ssl"))
    implementation(Dependencies.jnrUnixsocket)
    implementation(project(":wisp:wisp-resource-loader"))

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.junitApi)
}
