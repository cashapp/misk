plugins {
    `java-library`
}

sourceSets {
    val test by getting {
        java.srcDir("src/test/kotlin/")
    }
}

dependencies {
    api(libs.moshi)
    api(project(":wisp:wisp-config"))
    api(project(":wisp:wisp-feature"))
    implementation(project(":wisp:wisp-moshi"))

    testImplementation(libs.assertj)
    testImplementation(libs.hopliteCore)
    testImplementation(libs.junitApi)
    testImplementation(libs.kotestAssertionsShared)
    testImplementation(libs.kotestCommon)
    testImplementation(project(":wisp:wisp-resource-loader"))
}
