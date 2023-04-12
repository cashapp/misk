plugins {
    `java-library`
}

sourceSets {
    val main by getting {
        java.srcDir("src/main/kotlin/")
    }
}

dependencies {
    api(libs.okHttp)
    api(project(":wisp-ssl"))
    implementation(libs.jnrUnixsocket)
    implementation(project(":wisp-resource-loader"))

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
}
