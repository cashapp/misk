plugins {
    `java-library`
}

dependencies {
    implementation(libs.aws2Regions)
    api(project(":wisp-deployment"))

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
    testImplementation(project(":wisp-deployment-testing"))

}
