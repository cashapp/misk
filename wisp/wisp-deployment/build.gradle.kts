plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
		testImplementation(project(":wisp:wisp-deployment"))
    testImplementation(project(":wisp:wisp-deployment-testing"))
}
