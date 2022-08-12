plugins {
    `java-library`
}

sourceSets {
    val test by getting {
        java.srcDir("src/test/kotlin/")
    }
}

dependencies {
    implementation(Dependencies.kotlinStdLibJdk8)
    implementation(Dependencies.kotlinReflection)
    implementation(Dependencies.moshiCore)
    implementation(Dependencies.moshiKotlin)
    implementation(Dependencies.moshiAdapters)
    api(project(":wisp-config"))
    api(project(":wisp-feature"))
    api(project(":wisp-moshi"))
    api(project(":wisp-resource-loader"))

    testImplementation(testLibs.assertj)
    testImplementation(testLibs.bundles.kotest)
}
