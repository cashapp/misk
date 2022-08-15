plugins {
    `java-library`
}

sourceSets {
    val test by getting {
        java.srcDir("src/test/kotlin/")
    }
}

dependencies {
    implementation(libs.moshiCore)
    implementation(libs.moshiKotlin)
    implementation(libs.moshiAdapters)
    api(project(":wisp-config"))
    api(project(":wisp-feature"))
    api(project(":wisp-moshi"))
    api(project(":wisp-resource-loader"))

    testImplementation(libs.assertj)
    testImplementation(libs.bundles.kotest)
}
