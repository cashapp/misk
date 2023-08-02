plugins {
    `java-library`
}

sourceSets {
    val test by getting {
        java.srcDir("src/test/kotlin/")
    }
}

dependencies {
    api(Dependencies.moshi)
    api(project(":wisp:wisp-config"))
    api(project(":wisp:wisp-feature"))
    implementation(project(":wisp:wisp-moshi"))

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.hopliteCore)
    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.kotestAssertionsShared)
    testImplementation(Dependencies.kotestCommon)
    testImplementation(project(":wisp:wisp-resource-loader"))
}
