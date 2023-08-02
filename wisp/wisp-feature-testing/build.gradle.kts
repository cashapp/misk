plugins {
    `java-library`
}

sourceSets {
    val test by getting {
        java.srcDir("src/test/kotlin/")
    }
}

dependencies {
    api(libs.moshiCore)
    api(project(":wisp-config"))
    api(project(":wisp-feature"))
    implementation(project(":wisp-moshi"))

    testImplementation(libs.assertj)
    testImplementation(libs.hopliteCore)
    testImplementation(libs.junitApi)
    testImplementation(libs.kotestAssertionsShared)
    testImplementation(libs.kotestCommon)
    testImplementation(project(":wisp-resource-loader"))
}
