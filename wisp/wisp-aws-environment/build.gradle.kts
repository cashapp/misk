plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.aws2Bom))
    implementation(libs.aws2Regions)
    api(project(":wisp-deployment"))

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
    testImplementation(project(":wisp-deployment-testing"))

}
