plugins {
    `java-library`
}

dependencies {
    implementation(libs.kotlinStdLibJdk8)
    implementation(libs.kotlinReflection)
    api(libs.launchDarkly)
    api(libs.moshiKotlin)
    implementation(project(":wisp-client"))
    implementation(project(":wisp-feature"))
    implementation(project(":wisp-logging"))
    implementation(project(":wisp-ssl"))
    api(project(":wisp-config"))

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.mockitoCore)
    testImplementation(libs.moshiKotlin)
    testImplementation(libs.moshiAdapters)
    testImplementation(project(":wisp-moshi"))

}
