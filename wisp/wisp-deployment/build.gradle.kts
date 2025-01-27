plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
		testImplementation(project(":wisp:wisp-deployment"))
    testImplementation(project(":wisp:wisp-deployment-testing"))
}
